import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getDatabase } from 'firebase/database';

const databaseURL = import.meta.env.VITE_FIREBASE_DATABASE_URL?.trim();
export const configurationError = databaseURL
  ? ''
  : 'Set VITE_FIREBASE_DATABASE_URL in ui/.env.local for local development or ui/.env.production for a production build, then rebuild the app.';

const app = initializeApp({
  apiKey: 'AIzaSyAn3ig9__1Z9xSCxNFTGwlrCHSp16OAsuE',
  authDomain: 'soen343-f701c.firebaseapp.com',
  projectId: 'soen343-f701c',
  storageBucket: 'soen343-f701c.firebasestorage.app',
  messagingSenderId: '308043221565',
  appId: '1:308043221565:web:d358b0649a5eacfd05b854',
  measurementId: 'G-6180ED7X1Q',
  ...(databaseURL ? { databaseURL } : {}),
});

export const auth = getAuth(app);
export const database = databaseURL ? getDatabase(app) : null;
