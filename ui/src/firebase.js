import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getDatabase } from 'firebase/database';

const databaseURL = import.meta.env.VITE_FIREBASE_DATABASE_URL?.trim();
const apiKey = import.meta.env.VITE_FIREBASE_API_KEY?.trim();
export const configurationError = databaseURL && apiKey
  ? ''
  : 'Set VITE_FIREBASE_API_KEY and VITE_FIREBASE_DATABASE_URL for the build, then rebuild the app.';

const app = initializeApp({
  apiKey: apiKey || '',
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
