# Clutch_HI
Repository for SOEN343 project. Team name is Clutch_HI

#### To run the project
1. Install Node.js with npm and JDK 17 or newer.
2. From the repo root, run `npm install`.
3. Run `npm run dev`.
4. Open the local URL printed by Vite.

Local runs will run with the seeded accounts already present.

#### City map and delivery simulation
- The dashboard shows a real map of Montreal (OpenStreetMap tiles through Leaflet), so the browser needs internet access.
- Backend coordinates are kilometres east (`x`) and north (`y`) of `map.originLatitude` / `map.originLongitude` in `backend/config/demo.properties`. Courier assignment uses the straight-line distance between those points (R-DLV-02).
- Customers choose a delivery address by searching (OpenStreetMap Nominatim), picking a suggested place or tapping the map.
- Couriers travel in a straight line at `simulation.courierKmPerSecond` (sped up for the demo). Set it to `0` to turn the travel simulation off.
- Pricing values are read from the same file: `pricing.deliveryFee`, `pricing.serviceFee`, `pricing.smallOrderThreshold` and `pricing.smallOrderSurcharge`.
- The operator's **Reset simulation** button returns orders, the ledger, queues and couriers to the seeded state.

### Team members:

Louay Dridi - 40309145
Mohammed Hussain - 40314906
Elena Ferzli - 40315102
Daniel Hanna - 40312879
Ghassan Naja - 40264348

Document: https://docs.google.com/document/d/1Gg9-kgObzLt8J57qHLWS0km5tIHIIYyZbvbtC620zlw/edit?tab=t.0#heading=h.pls9o7v02vmt
