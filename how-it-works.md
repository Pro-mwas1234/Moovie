
# How It Works & Data Sources

This document explains how each part of the movie app (see `plan.md`) actually works under the hood, and where the data comes from. It maps every screen and feature to the API calls, storage, and third-party services that power it.

---

## 1. Core Data Sources

The app is a **client** for movie metadata + a **backend** that stores user-specific data. It does not host movies itself.

### Primary movie metadata

| Source | What it gives you | Notes |
|--------|-------------------|-------|
| **TMDB (The Movie Database)** — https://developer.themoviedb.org | Titles, posters, backdrops, cast/crew, genres, ratings, trailers (YouTube keys), "similar" titles, trending, images | Free API key, generous limits, the standard choice for hobby + indie apps. This is the recommended primary source. |
| **OMDb API** — https://www.omdbapi.com | IMDb/Rotten Tomatoes/Metacritic ratings, plot, awards | Good for enriching ratings TMDB doesn't have. Free tier = 1,000 req/day. |
| **JustWatch / Streaming Availability API** — https://www.movieofthenight.com/about/api or TMDB's `/watch/providers` | "Where to Watch" streaming logos + deep links per region | TMDB provides watch-provider data powered by JustWatch (attribution required). Use TMDB `/movie/{id}/watch/providers`. |

### Media assets

- **Posters & backdrops**: served from TMDB's image CDN (`https://image.tmdb.org/t/p/<size>/<path>`). You store only the path, build the URL client-side with the size you need.
- **Trailers**: TMDB returns YouTube video keys; embed via the YouTube player or open a deep link.

### User data (your own backend)

Anything personal is **not** from TMDB — you own it:

- Accounts, auth, profile
- Watchlist (want / watching / watched)
- Ratings & reviews
- Onboarding preferences (genres, streaming services)
- Continue-watching progress
- Friends / following, watch-party sessions

Recommended stack: any backend (Node/Express, FastAPI, Supabase, Firebase). A managed BaaS like **Supabase** or **Firebase** covers auth + database + realtime (needed for Watch Party) with the least setup.

---

## 2. Screen-by-Screen: How Each Works

### Splash / Onboarding
- **Slides**: static local content, no network.
- **Pick genres**: fetched once from TMDB `/genre/movie/list`; selections saved to the user's profile in your backend.
- **Pick streaming services**: list from TMDB `/watch/providers/movie` (filtered by region); selections saved to profile.
- **Get Started**: writes preferences → backend, then routes to Home.

### Home (discovery hub)
Each horizontal row is one API call:

| Row | Source |
|-----|--------|
| Featured / Hero | TMDB `/trending/movie/week` (pick #1) or an editorially-set ID |
| Continue Watching | **Your backend** — per-user progress records |
| Trending Now | TMDB `/trending/movie/day` |
| New Releases | TMDB `/movie/now_playing` |
| Because You Liked ___ | TMDB `/movie/{id}/recommendations` seeded from a title the user rated highly |
| Top 10 This Week | TMDB `/trending/movie/week` (first 10) |

Rows are cached client-side (e.g. 30–60 min) to save quota and load fast.

### Search
- **Text search**: TMDB `/search/multi` (movies, TV, people) — matches the Movies/TV/People toggle.
- **Filters** (genre, year, rating, provider): TMDB `/discover/movie` with query params (`with_genres`, `primary_release_year`, `vote_average.gte`, `with_watch_providers`).
- **Recent searches**: stored locally on device (local storage / AsyncStorage).
- **Trending searches**: TMDB `/trending/...` or your backend's aggregated query log.
- Debounce input (~300ms) so you don't fire a request per keystroke.

### Movie Detail
One title, several calls (batch with `append_to_response` to do it in a single request):

- Core info: TMDB `/movie/{id}?append_to_response=credits,videos,similar,release_dates`
  - Title, year, runtime, genres, synopsis → main object
  - Cast & Crew → `credits`
  - Trailer → `videos` (YouTube key)
  - More Like This → `similar` or `recommendations`
  - Age rating / content warnings → `release_dates` (certification per region)
- Extra ratings (IMDb/RT): OMDb by IMDb ID.
- Where to Watch: TMDB `/movie/{id}/watch/providers` → logos + JustWatch deep links.
- Buttons write to **your backend**: `+ Watchlist`, `⭐ Rate`, and Share (generates a link to the detail screen).

### Watchlist
- Entirely **your backend**. Three states (want / watching / watched) are a status field on each saved item.
- Stores the TMDB id + minimal cached fields (title, poster path) so the list renders without re-fetching every title.
- Sort/filter done client-side or via query params.
- Swipe-to-remove → DELETE on the backend record.

### Discover (mood / genre)
- **Genre browse**: TMDB `/discover/movie?with_genres=<id>`.
- **Mood chips**: your own mapping of mood → genre/keyword IDs (e.g. "Scary" → Horror genre 27; "Mind-bend" → keyword/Sci-Fi). Stored as a small config in the app or backend.
- **Surprise Me**: pick a random page + random item from `/discover/movie`.

### Profile
- Identity, avatar, bio, following → **your backend**.
- Stats (movies watched, hours, favorite genre) → computed by your backend from the user's watched list (sum runtimes, tally genres).
- My Ratings → your backend, each linking back to a TMDB title.

### Settings
- All values live in **your backend** / device settings.
- Connected streaming services feed the provider filters elsewhere.
- Dark mode = local device preference.
- Delete account = cascade delete of the user's backend records.

### Watch Party (Phase 3)
- **Synced playback + chat** needs a **realtime layer**: WebSockets (Socket.IO) or a managed realtime DB (Firebase Realtime DB / Supabase Realtime).
- One user is host; playback state (play/pause/seek + timestamp) is broadcast to the room.
- Chat messages persisted per session in your backend.
- Note: the app syncs *state*, it doesn't stream the actual video — real playback still happens via the streaming provider's app/deep link.

---

## 3. Recommendations (“Because You Liked” / AI Recommend)

- **Simple, no ML**: use TMDB `/movie/{id}/recommendations` and `/discover` filtered by the user's top genres. Good enough for MVP.
- **AI Recommend chat** (Phase 3, "something like Inception but funnier"): send the prompt to an LLM API (e.g. OpenAI) with a system prompt that returns title suggestions, then resolve each title via TMDB search to get posters/details. The LLM suggests; TMDB supplies the real data.

---

## 4. Data Flow (typical request)

```
User action (tap row / search / open detail)
        │
        ▼
App checks local cache ──hit──▶ render instantly
        │ miss
        ▼
   Is it personal data?
   ├─ Yes ─▶ Your backend API ─▶ DB (watchlist, ratings, profile)
   └─ No  ─▶ TMDB / OMDb API ─▶ cache result ─▶ render
        │
        ▼
Build image URLs from TMDB CDN + display
```

Personal writes (add to watchlist, rate, update settings) always go to **your backend**; movie metadata always comes from **TMDB/OMDb** and is cached.

---

## 5. Auth & Security

- Auth via your backend / BaaS (email+password, OAuth). Never store movie-API keys in the client — proxy TMDB/OMDb/LLM calls through your backend so keys stay server-side and you can rate-limit + cache centrally.
- All API traffic over HTTPS.
- Parental controls filter results using TMDB certification data (`release_dates`) and the `include_adult=false` flag.

---

## 6. Suggested Tech Stack (fill in your choices)

| Layer | Option |
|-------|--------|
| Client | React Native / Flutter (mobile), or React (web) |
| State/cache | React Query / SWR (handles caching + refetch nicely) |
| Backend | Supabase / Firebase (fastest) or Node+Postgres / FastAPI |
| Realtime (Watch Party) | Supabase Realtime / Firebase / Socket.IO |
| Movie data | TMDB (primary) + OMDb (rating enrichment) |
| Streaming availability | TMDB watch providers (JustWatch) |

---

## 7. Attribution & Limits (don't skip)

- **TMDB** requires attribution ("This product uses the TMDB API but is not endorsed or certified by TMDB") + their logo. Free but rate-limited — cache aggressively.
- **JustWatch** data (via TMDB providers) requires attribution too.
- **OMDb** free tier is 1,000 requests/day — cache ratings on your backend.
- Respect each provider's terms before going to production, especially around commercial use.

---

## Sources / References

- TMDB API docs: https://developer.themoviedb.org/docs
- TMDB image basics: https://developer.themoviedb.org/docs/image-basics
- TMDB watch providers: https://developer.themoviedb.org/reference/movie-watch-providers
- OMDb API: https://www.omdbapi.com
- Streaming Availability API: https://www.movieofthenight.com/about/api
- Supabase docs: https://supabase.com/docs
- Firebase docs: https://firebase.google.com/docs
- React Query: https://tanstack.com/query
