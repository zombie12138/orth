import { Routes, Route, Navigate } from 'react-router';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
