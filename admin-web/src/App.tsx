import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { OverviewPage } from './pages/OverviewPage';
import { UsersPage } from './pages/UsersPage';
import { ListingsPage } from './pages/ListingsPage';
import { OrdersPage } from './pages/OrdersPage';
import { PayoutsPage } from './pages/PayoutsPage';
import { RefundsPage } from './pages/RefundsPage';
import { DisputesPage } from './pages/DisputesPage';
import { FlaggedMessagesPage } from './pages/FlaggedMessagesPage';
import { CommissionReportPage } from './pages/CommissionReportPage';
import { SettingsPage } from './pages/SettingsPage';
import { AuditLogPage } from './pages/AuditLogPage';

const queryClient = new QueryClient();

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route path="/" element={<Layout />}>
            <Route index element={<OverviewPage />} />
            <Route path="users" element={<UsersPage />} />
            <Route path="listings" element={<ListingsPage />} />
            <Route path="orders" element={<OrdersPage />} />
            <Route path="payouts" element={<PayoutsPage />} />
            <Route path="refunds" element={<RefundsPage />} />
            <Route path="disputes" element={<DisputesPage />} />
            <Route path="flagged-messages" element={<FlaggedMessagesPage />} />
            <Route path="commission" element={<CommissionReportPage />} />
            <Route path="settings" element={<SettingsPage />} />
            <Route path="audit-log" element={<AuditLogPage />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
};

export default App;
