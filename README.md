# Clutch_HI
Repository for SOEN343 project. Team name is Clutch_HI

Run `npm run dev` to start the frontend and Java backend with Firebase login and local demo data. The launcher loads the accounts and Pastry Montreal/Nice Ice restaurants in `backend/config/demo.properties`, plus the per-role profiles in `backend/config/demo-profiles.properties`. The launcher reads the Firebase database URL from the environment, `ui/.env.local`, or `ui/.env.production` for login. Demo orders and edits to seeded restaurants reset when the backend restarts.

To use the deployed Firebase restaurants during local development, set `SUGARRUSH_SEED=backend/config/live.properties` before running `npm run dev`. The deployed service continues to use its own configuration in `render.yaml`.

The demo usernames/passwords in `demo.properties` are backend fixtures, not Firebase login credentials. The UI signs in through Firebase; the configured Firebase UIDs in the seed file map existing Firebase users to the restaurant, courier, and operator demo accounts. An unmapped Firebase user remains a customer with their own profile.

Team members:

Louay Dridi - 40309145

Mohammed Hussain - 40314906

Elena Ferzli - 40315102

Daniel Hanna - 40312879

Ghassan Naja - 40264348

Document: https://docs.google.com/document/d/1Gg9-kgObzLt8J57qHLWS0km5tIHIIYyZbvbtC620zlw/edit?tab=t.0#heading=h.pls9o7v02vmt
