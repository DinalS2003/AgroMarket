/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        agro: {
          primary: '#2E7D32',
          dark: '#1B5E20',
          light: '#C8E6C9',
          brown: '#8D6E63',
          bg: '#F6F4EC',
        },
      },
    },
  },
  plugins: [],
};
