# Moovie Embed Server (Node.js)

A Node.js reimplementation of the Python embed server for Moovie movie streaming.

## Setup

```bash
cd node-movie
npm install
```

## Usage

```bash
npm start
```

Server runs on `http://127.0.0.1:8080`.

## Endpoints

- `GET /` - Serves the HTML5 player (dash.js)
- `GET /resolve?type=movie&id=<TMDB_ID>` - Returns a manifest URL (proxy token)
- `GET /proxy?u=<base64url>` - Proxies manifest/segment requests with proper Referer
- `POST /download` - (placeholder) Start download
- `GET /download/:id/status` - (placeholder) Check download status
- `GET /download/:id/file` - (placeholder) Download file

## Notes

- The extractor logic (Playwright) is not implemented; you must integrate your own stream extraction function.
- Replace the placeholder in `/resolve` with actual extraction to get real manifest URLs.
- For movie-only usage, call `/resolve?type=movie&id=<TMDB_ID>`.