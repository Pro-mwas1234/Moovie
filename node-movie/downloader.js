#!/usr/bin/env node
/**
 * Moovie Downloader — standalone CLI movie downloader.
 *
 * Uses the OmniSave/videodownloader.site public BFF API:
 *   1. POST /subject/search-suggest (anonymous) -> mints a guest JWT via `x-user` header
 *   2. POST /subject/search                      -> find movies/shows by keyword
 *   3. GET  /subject/download                    -> signed direct MP4 + subtitle URLs
 *   4. Streams the MP4 to disk with progress
 *
 * Usage:
 *   node downloader.js search "Deadpool"
 *   node downloader.js download "Deadpool" --quality 720
 *   node downloader.js download "Deadpool" --quality 720 --subtitle en
 *   node downloader.js info "Deadpool"          (list qualities/subs, no download)
 */

const fs = require('fs');
const path = require('path');

const USAGE = `Usage:
  node downloader.js search "Deadpool"
  node downloader.js info "Deadpool"
  node downloader.js download "Deadpool" --quality 720
  node downloader.js download "Deadpool" --quality 720 --subtitle en
Options:
  --quality N   pick a resolution (360 | 480 | 720), defaults to best available
  --subtitle L  also download subtitle by language code (en, fr, ar, ...)
  --out DIR     output directory (default: ./downloads)`;

const API = 'https://h5-api.aoneroom.com/wefeed-h5api-bff';
const ORIGIN = 'https://videodownloader.site';

const baseHeaders = {
  'accept': 'application/json',
  'content-type': 'application/json',
  'x-source': 'downloader',
  'x-request-lang': 'en',
  'origin': ORIGIN,
  'referer': ORIGIN + '/',
  'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0 Safari/537.36',
};

let TOKEN = null;

async function api(pathname, { method = 'GET', body } = {}) {
  const headers = { ...baseHeaders };
  if (TOKEN) headers.authorization = 'Bearer ' + TOKEN;
  const r = await fetch(API + pathname, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  // server mints a guest token on anonymous calls; refresh ours when present
  const xUser = r.headers.get('x-user');
  if (xUser) {
    try {
      const t = JSON.parse(xUser).token;
      if (t) TOKEN = t;
    } catch { /* ignore */ }
  }
  const json = await r.json().catch(() => null);
  if (!json || json.code !== 0) {
    throw new Error(`${method} ${pathname} failed: HTTP ${r.status} ${JSON.stringify(json).slice(0, 300)}`);
  }
  return json.data;
}

async function ensureToken() {
  if (TOKEN) return TOKEN;
  await api('/subject/search-suggest', { method: 'POST', body: { keyword: 'a', perPage: 1 } });
  if (!TOKEN) throw new Error('Could not obtain guest token from API');
  return TOKEN;
}

function fmtSize(bytes) {
  bytes = Number(bytes) || 0;
  if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(1) + ' GB';
  if (bytes >= 1048576) return (bytes / 1048576).toFixed(0) + ' MB';
  return (bytes / 1024).toFixed(0) + ' KB';
}

async function search(keyword, perPage = 20) {
  await ensureToken();
  const data = await api('/subject/search', {
    method: 'POST',
    body: { keyword, page: 1, perPage, subjectType: 0 },
  });
  return data.items.map(it => ({
    subjectId: it.subjectId,
    title: it.title,
    type: it.subjectType === 1 ? 'Movie' : 'TV Series',
    releaseDate: it.releaseDate,
    genre: it.genre,
    imdb: it.imdbRatingValue,
    durationMin: Math.round((it.duration || 0) / 60),
    detailPath: it.detailPath,
    hasResource: it.hasResource,
  }));
}

async function getDownloads(subjectId, detailPath) {
  await ensureToken();
  const q = `subjectId=${subjectId}&se=0&ep=0&detailPath=${encodeURIComponent(detailPath)}&supportCodecs%5Bh264%5D=1`;
  return api('/subject/download?' + q);
}

function pickQuality(downloads, quality) {
  if (!downloads.length) return null;
  if (quality) {
    const hit = downloads.find(d => String(d.resolution) === String(quality));
    if (hit) return hit;
    console.warn(`Quality ${quality}P not available; falling back to best.`);
  }
  return downloads.reduce((a, b) => (Number(b.resolution) > Number(a.resolution) ? b : a));
}

async function downloadFile(url, dest, label) {
  const r = await fetch(url, { headers: { 'referer': ORIGIN + '/' } });
  if (!r.ok) throw new Error(`Download failed: HTTP ${r.status}`);
  const total = Number(r.headers.get('content-length')) || 0;
  let seen = 0, lastPct = -1;
  const out = fs.createWriteStream(dest);
  for await (const chunk of r.body) {
    out.write(chunk);
    seen += chunk.length;
    if (total) {
      const pct = Math.floor((seen / total) * 100);
      if (pct !== lastPct && pct % 5 === 0) {
        lastPct = pct;
        process.stdout.write(`\r    ${label}: ${pct}% (${fmtSize(seen)} / ${fmtSize(total)})   `);
      }
    }
  }
  await new Promise(res => out.end(res));
  process.stdout.write(`\r    ${label}: done (${fmtSize(seen)})            \n`);
  return seen;
}

function safeName(s) {
  return s.replace(/[<>:"/\\|?*]+/g, '').replace(/\s+/g, ' ').trim().slice(0, 120);
}

async function main() {
  const args = process.argv.slice(2);
  const cmd = args[0];
  if (!cmd || ['help', '-h', '--help'].includes(cmd)) {
    console.log(USAGE);
    return;
  }

  const query = args[1];
  if (!query) { console.error('Provide a title, e.g.: node downloader.js search "Deadpool"'); process.exit(1); }

  const getOpt = (name) => {
    const i = args.indexOf(name);
    return i >= 0 ? args[i + 1] : null;
  };

  if (cmd === 'search') {
    const results = await search(query);
    console.log(`\nFound ${results.length} results for "${query}":\n`);
    for (const [i, m] of results.entries()) {
      console.log(`${String(i + 1).padStart(2)}. ${m.title} (${m.releaseDate}) [${m.type}]` +
        ` IMDB:${m.imdb ?? '-'} ${m.durationMin}min ${m.hasResource ? '✓ downloadable' : '✗'}`);
    }
    console.log(`\nDownload with: node downloader.js download "${query}" --quality 720`);
    return;
  }

  if (cmd === 'info' || cmd === 'download') {
    const quality = getOpt('--quality') || getOpt('-q');
    const subtitle = getOpt('--subtitle') || getOpt('-s');
    const outDir = getOpt('--out') || path.join(process.cwd(), 'downloads');

    console.log(`Searching "${query}"...`);
    const results = (await search(query)).filter(m => m.hasResource);
    if (!results.length) { console.error('No downloadable results.'); process.exit(1); }
    const pick = results[0];
    console.log(`\n▶ ${pick.title} (${pick.releaseDate}) [${pick.type}] IMDB:${pick.imdb}`);

    console.log('Resolving download links...');
    const data = await getDownloads(pick.subjectId, pick.detailPath);
    const downloads = (data.downloads || []).filter(d => !d.vipLocked);
    const captions = data.captions || [];

    console.log('\nQualities:');
    downloads.forEach((d, i) =>
      console.log(`  ${i + 1}. ${d.resolution}P ${d.format} ${fmtSize(d.size)}${d.vipLocked ? ' (VIP)' : ''}`));
    if (captions.length) {
      console.log('\nSubtitles:');
      captions.forEach((c, i) => console.log(`  ${i + 1}. ${c.lanName} (${c.lan}) ${fmtSize(c.size)}`));
    }

    if (cmd === 'info') return;

    fs.mkdirSync(outDir, { recursive: true });
    const base = safeName(`${pick.title} (${(pick.releaseDate || '').slice(0, 4)})`);

    const chosen = pickQuality(downloads, quality);
    if (chosen) {
      const ext = chosen.format.toLowerCase() || 'mp4';
      const dest = path.join(outDir, `${base}.${chosen.resolution}p.${ext}`);
      console.log(`\nDownloading ${chosen.resolution}P -> ${dest}`);
      await downloadFile(chosen.url, dest, chosen.resolution + 'P video');
    }

    if (subtitle) {
      const cap = captions.find(c => c.lan === subtitle) ||
                  captions.find(c => c.lanName.toLowerCase().startsWith(subtitle.toLowerCase()));
      if (cap) {
        const dest = path.join(outDir, `${base}.${cap.lan}.srt`);
        console.log(`Downloading subtitle (${cap.lanName}) -> ${dest}`);
        await downloadFile(cap.url, dest, cap.lanName);
      } else {
        console.warn(`Subtitle "${subtitle}" not found. Available: ${captions.map(c => c.lan).join(', ')}`);
      }
    }
    console.log('\n✔ Saved to ' + outDir);
    return;
  }

  console.error(`Unknown command "${cmd}". Use search | info | download.`);
  process.exit(1);
}

main().catch(e => { console.error('Error:', e.message); process.exit(1); });
