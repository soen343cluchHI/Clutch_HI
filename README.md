# Clutch_HI
Repository for SOEN343 project. Team name is Clutch_HI

#### To run the project
1. Install Node.js with npm and JDK 17 or newer.
2. From the repo root, run `npm install`.
3. Make sure `ui/.env.local` has the Firebase web settings, including `VITE_FIREBASE_DATABASE_URL`.
4. Run `npm run dev`.
5. Open the local URL printed by Vite and sign in with a Firebase account (or create one and pick its role).

There is no seed data. Accounts, restaurants and menus always live in the Firebase Realtime Database, locally and when deployed. Settings (pricing, map, courier speed) are in `backend/config/app.properties`.

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
