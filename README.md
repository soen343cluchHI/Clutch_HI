# Clutch_HI
Repository for SOEN343 project. Team name is Clutch_HI

#### To run the project
1. Install Node.js with npm and JDK 17 or newer.
2. From the repo root, run `npm install`.
3. Make sure `ui/.env.local` has the Firebase web settings, including `VITE_FIREBASE_API_KEY` and `VITE_FIREBASE_DATABASE_URL`.
4. Run `npm run dev`.
5. Open the local URL printed by Vite and sign in with a Firebase account (or create one and pick its role).

In normal mode (`DEMO_MODE` unset or `false`), accounts, restaurants and menus live in Firebase Realtime Database. Settings (pricing, map, courier speed) are in `backend/config/app.properties`.

#### Self-contained course demo

`demo/compose.yaml` runs the `ui` nginx image and the `backend` Java image with `DEMO_MODE=true`. The backend creates four fake accounts and seeds a local restaurant data file on an empty `demodata` volume. The seed in `backend/demo/seed.json` contains only two generic restaurants and three menu items derived from a sanitized Firebase export. It contains no real accounts, Firebase IDs, addresses, or credentials. Restaurants and menu changes persist in the volume; orders and sessions use the existing in-memory model and reset when the backend restarts.

Demo login credentials (all use password `demo1234`):

| Role | Email |
|---|---|
| Customer | `customer@demo.local` |
| Restaurant | `restaurant@demo.local` |
| Courier | `courier@demo.local` |
| Operator | `operator@demo.local` |

Pricing, promotions, ordering, preparation, dispatch, pickup, and delivery use the existing command and business logic. The demo UI chooses the local sign-in flow after `/api/mode` reports demo mode. Normal Firebase mode remains the default. For a normal production frontend build, supply `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_DATABASE_URL`, and `VITE_API_URL` as build-time variables; none is built into the demo image by default. Restrict any Firebase browser key used for normal production by HTTP referrer.

The existing Render backend still needs `SUGARRUSH_DATABASE_URL` in its environment. The Blueprint prompts for it on a new setup; for an existing Render service, set or confirm it in the Render dashboard before redeploying.

The publish workflow uses `SERVICES: "backend ui"`. After its first successful run, replace both placeholder `image:` tags in `demo/compose.yaml` with the commit SHA lines from the workflow summary. The workflow publishes on the default branch or a tag; this `demo-platform` branch alone does not publish. To rehearse after publishing, run `docker compose -f demo/compose.yaml -f demo/rehearse.yaml up -d` and open `http://127.0.0.1:8088`. Run `backend/check.ps1` and `npm test --prefix ui` for the automated checks.

Optional demo shortcuts for local runs: add test accounts to `ui/.env.local` (ignored by git) and the sign-in page shows one button per account:
`VITE_DEMO_ACCOUNTS='[{"label":"Courier","email":"courier@example.com","password":"secret"}]'`

After changing `database.rules.json`, deploy it with `firebase deploy --only database`.

#### City map and delivery simulation
- The dashboard shows a real map of Montreal (OpenStreetMap tiles through Leaflet), so the browser needs internet access.
- Backend coordinates are kilometres east (`x`) and north (`y`) of `map.originLatitude` / `map.originLongitude` in `backend/config/app.properties`. Courier assignment uses the straight-line distance between those points (R-DLV-02).
- Customers choose a delivery address by searching (OpenStreetMap Nominatim), picking a suggested place or tapping the map.
- Couriers travel in a straight line at `simulation.courierKmPerSecond` (sped up for the demo). Set it to `0` to turn the travel simulation off.
- Pricing values are read from the same file: `pricing.deliveryFee`, `pricing.serviceFee`, `pricing.smallOrderThreshold` and `pricing.smallOrderSurcharge`.
- The operator's **Reset simulation** button deletes every restaurant (also in Firebase), every order and ledger entry, and puts couriers off duty. Accounts are kept, so restaurant owners can open a new restaurant right away.

### Team members:

Louay Dridi - 40309145
Mohammed Hussain - 40314906
Elena Ferzli - 40315102
Daniel Hanna - 40312879
Ghassan Naja - 40264348

Document: https://docs.google.com/document/d/1Gg9-kgObzLt8J57qHLWS0km5tIHIIYyZbvbtC620zlw/edit?tab=t.0#heading=h.pls9o7v02vmt
