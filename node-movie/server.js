const express = require('express');
const { URL } = require('url');
const fetch = (...args) => import('node-fetch').then(({default: fetch}) => fetch(...args));
const app = express();
const PORT = process.env.PORT || 8082;

const UPSTREAM_REFERER = 'https://vidstuck.xyz/'; 
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';
const ALLOWED_HOSTS = new Set(['vidstuck.xyz', 'sacdn.hakunaymatata.com']);
const { spawn } = require('child_process');
const path = require('path');

function allowed(urlString) {
  try {
    const host = new URL(urlString).hostname.toLowerCase();
    return ALLOWED_HOSTS.has(host) || [...ALLOWED_HOSTS].some(h => host.endsWith('.' + h));
  } catch (_) {
    return false;
  }
}

async function fetchWithReferer(urlString) {
  const resp = await fetch(urlString, {
    headers: {
      'User-Agent': UA,
      'Referer': UPSTREAM_REFERER,
      'Origin': UPSTREAM_REFERER.replace(/\/+$/, ''),
      'Accept': '*/*'
    }
  });
  if (!resp.ok) throw new Error(`Upstream error: ${resp.status}`);
  const body = await resp.buffer();
  const contentType = resp.headers.get('content-type') || 'application/octet-stream';
  return { body, contentType };
}

function base64UrlEncode(str) {
  return Buffer.from(str).toString('base64url');
}
function base64UrlDecode(token) {
  return Buffer.from(token, 'base64url').toString('utf8');
}

// Serve player HTML
const PLAYER_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Moovie Player</title>
</head>
<body>
<div id="brand">__BRANDING__</div>
<video id="v" controls playsinline></video>
<script src="https://cdn.dashjs.org/latest/dash.all.min.js"></script>
<script>
(function(){
  const v = document.getElementById('v');
  const params = new URLSearchParams(location.search);
  const type = params.get('type');
  const id = params.get('id') || params.get('movie') || params.get('tv');
  if (!id) { alert('Missing id'); return; }
  fetch('/resolve?type=' + encodeURIComponent(type || 'movie') + '&id=' + encodeURIComponent(id))
    .then(r => r.json())
    .then(d => {
      const player = dashjs.MediaPlayer().create();
      // Prefer video track if available
      player.updateSettings({
        streaming: {
          // Ensure we start with video if possible
          preferredAudioLanguage: undefined
        }
      });
      player.initialize(v, d.manifest, true);
      // After manifest loads, select first video track
      player.on(dashjs.MediaPlayer.events.MANIFEST_LOADED, () => {
        const videoTracks = player.getVideoTracks();
        if (videoTracks.length > 0) {
          player.setTrack(videoTracks[0].id);
        }
      });
    })
    .catch(err => { alert('Error: ' + err); });
})();
</script>
</body>
</html>`;

app.get('/', (req, res) => {
  const branding = req.query.branding || 'Moovie';
  const html = PLAYER_HTML.replace('__BRANDING__', branding.replace(/[<">]/g, ''));
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.send(html);
});

app.get('/resolve', async (req, res) => {
  const media = req.query.type || 'tv';
  const tid = req.query.tv || req.query.movie || req.query.id;
  const season = req.query.s || '1';
  const ep = req.query.e || '1';
  if (!tid) return res.status(400).json({error: 'missing id'});

  const embed = media === 'movie'
    ? `https://vidstuck.xyz/embed/movie/${tid}`
    : `https://vidstuck.xyz/embed/tv/${tid}/${season}/${ep}`;

  // Run extractor script
  const result = await new Promise((resolve, reject) => {
    const pythonProcess = spawn('python', [
      path.resolve(__dirname, '../movie-scraper/test/extract_stream.py'),
      embed,
      '--timeout', '45'
    ], { stdio: ['pipe', 'pipe', 'pipe'] });

    let output = '';
    pythonProcess.stdout.on('data', (data) => {
      output += data.toString();
    });
    pythonProcess.stderr.on('data', (data) => {
      console.error('Extractor stderr:', data.toString());
    });
    pythonProcess.on('close', (code) => {
      if (code !== 0) {
        reject(new Error(`Extractor exited with code ${code}`));
        return;
      }
      try {
        const parsed = JSON.parse(output);
        resolve(parsed);
      } catch (e) {
        reject(new Error('Failed to parse extractor output: ' + e.message));
      }
    });
  });

  if (!result || !result.streams || !result.streams.length) {
    return res.status(404).json({error: 'No stream found'});
  }

  const stream = result.streams.find(s => ['dash','hls','mp4','video'].includes(s.kind)) || result.streams[0];
  const upstream = stream.url;

  const manifestToken = base64UrlEncode(upstream);
  const manifestUrl = `/proxy?u=${manifestToken}`;
  const response = {kind: stream.kind, manifest: manifestUrl};
  // Optionally add duration/qualities if dash
  if (stream.kind === 'dash') {
    // TODO: fetch manifest to get duration/qualities if needed
  }
  res.json(response);
});

app.get('/proxy', async (req, res) => {
  const token = req.query.u;
  if (!token) return res.status(400).send('bad url token');
  let url;
  try { url = base64UrlDecode(token); } catch (_) { return res.status(400).send('bad url token'); }
  if (!url || !allowed(url)) return res.status(403).send('host not allowed');
  try {
    const {body, contentType} = await fetchWithReferer(url);
    let ctype = contentType;
    let out = body;
    // If manifest, rewrite relative URLs to absolute (optional)
    if (ctype.includes('dash+xml') || url.endsWith('.mpd')) {
      const text = out.toString('utf-8');
      // Simple rewrite: we could keep as-is; for now just pass through
      out = Buffer.from(text);
      ctype = 'application/dash+xml';
    }
    res.set({
      'Content-Type': ctype,
      'Access-Control-Allow-Origin': '*',
      'Cache-Control': 'public, max-age=30'
    });
    send(res, out);
  } catch (err) {
    res.status(502).send('upstream error: ' + err.message);
  }
});

function send(res, data) {
  if (typeof data === 'string') res.send(data);
  else res.send(data);
}

// Placeholder download endpoints - implement as needed
app.post('/download', (req, res) => res.status(501).send('Not implemented'));
app.get('/download/:jobId/status', (req, res) => res.status(501).send('Not implemented'));
app.get('/download/:jobId/file', (req, res) => res.status(501).send('Not implemented'));

app.listen(PORT, '127.0.0.1', () => {
  console.log(`Server running at http://127.0.0.1:${PORT}`);
});