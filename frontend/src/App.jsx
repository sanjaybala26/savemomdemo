import React, { useState, useEffect, useRef } from 'react';
import {
  checkBackendHealth,
  registerUser,
  loginUser,
  fetchCurrentUser,
  fetchActiveProviders,
  fetchProviderSlots,
  fetchMyProviderProfile,
  updateMyProviderProfile,
  bookAppointment,
  fetchMyAppointments,
  cancelAppointment,
  confirmAppointment,
  completeAppointment,
  fetchAppointmentHistory,
  updateAppointmentDetails,
  fetchAppointmentById,
  fetchProviderQueue,
  callNextQueueToken,
  updateQueueTokenStatus,
  fetchMyActiveQueueToken,
  fetchMyQueueTokens,
  fetchAdminStats,
  fetchAdminUsers,
  fetchAdminProviders,
  fetchAdminAppointments,
  fetchAdminQueue,
  fetchAdminAnalytics,
  updateAdminUserRole,
  toggleAdminProviderActive,
  saveToken,
  clearStoredToken,
} from './services/api';
import './App.css';

// Application Timezone: Asia/Kolkata (IST UTC+05:30, consistent with backend)
const APP_TIMEZONE = 'Asia/Kolkata';

// Live Queue Tracking Configuration
const QUEUE_APPROACHING_THRESHOLD = 2; // Notify customer when 2 or fewer patients are ahead
const isTerminalQueueStatus = (status) => ['COMPLETED', 'CANCELLED', 'SKIPPED'].includes(status);

// Helper to get formatted today date YYYY-MM-DD in the clinic timezone
function getTodayString(dateObj = new Date(), timezone = APP_TIMEZONE) {
  try {
    return new Intl.DateTimeFormat('en-CA', { timeZone: timezone }).format(dateObj);
  } catch (e) {
    const year = dateObj.getFullYear();
    const month = String(dateObj.getMonth() + 1).padStart(2, '0');
    const day = String(dateObj.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}

// Global React Error Boundary to catch UI rendering crashes and prevent white screen
class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }
  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }
  componentDidCatch(error, errorInfo) {
    console.error("UI Render Error caught by ErrorBoundary:", error, errorInfo);
  }
  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: '2rem', margin: '1.5rem', border: '1px solid #fecaca', borderRadius: '10px', background: '#fff1f2', textAlign: 'center' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: '0.75rem' }}>⚠️</div>
          <h3 style={{ color: '#b91c1c', marginBottom: '0.5rem' }}>Dashboard Render Notice</h3>
          <p style={{ color: '#4b5563', fontSize: '0.9rem', maxWidth: '600px', margin: '0 auto 1rem auto' }}>
            {this.state.error?.message || 'A minor interface error occurred while rendering.'}
          </p>
          <button
            className="btn btn-primary"
            onClick={() => { this.setState({ hasError: false, error: null }); window.location.reload(); }}
          >
            ↻ Reload Application
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

// Helper to generate the next N upcoming dates for quick selection
function getUpcomingDates(numDays = 7, timezone = APP_TIMEZONE) {
  const dates = [];
  const now = new Date();
  for (let i = 0; i < numDays; i++) {
    const d = new Date(now.getTime() + i * 24 * 60 * 60 * 1000);
    const dateString = getTodayString(d, timezone);
    const fullDayOfWeek = d.toLocaleDateString('en-US', { weekday: 'long', timeZone: timezone }).toUpperCase();
    const label = i === 0 ? 'Today' : i === 1 ? 'Tomorrow' : d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', timeZone: timezone });
    dates.push({ dateString, fullDayOfWeek, label, rawDate: d });
  }
  return dates;
}

const ALL_DAYS_OF_WEEK = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

// Configuration for the three distinct role authentication portals
const ROLE_CONFIG = {
  ADMIN: {
    key: 'ADMIN',
    path: '/admin/auth',
    title: 'Admin Login or Register',
    subtitle: 'System Administrator Portal',
    description: 'Manage clinic staff, service providers, appointments, system configurations, and queue analytics.',
    icon: '🛡️',
    badgeClass: 'role-pill-admin',
    iconClass: 'role-icon-admin',
    cardClass: 'role-card-admin',
    calloutClass: 'callout-admin',
    calloutText: 'Administrator credentials required. Access to system KPIs, user directories, and audit logs is strictly protected.',
    portalTab: 'admin-dashboard',
    btnLabel: 'Admin',
  },
  PROVIDER: {
    key: 'PROVIDER',
    path: '/doctor/auth',
    title: 'Doctor Login or Register',
    subtitle: 'Healthcare Provider & Doctor Portal',
    description: 'Set your consultation hours, call waiting patients in order, and track your daily clinic queue.',
    icon: '🩺',
    badgeClass: 'role-pill-doctor',
    iconClass: 'role-icon-doctor',
    cardClass: 'role-card-doctor',
    calloutClass: 'callout-doctor',
    calloutText: 'Doctor / Provider credentials required. Please use the Patient portal if booking a consultation visit.',
    portalTab: 'clinic-queue',
    btnLabel: 'Doctor',
  },
  CUSTOMER: {
    key: 'CUSTOMER',
    path: '/patient/auth',
    title: 'Patient Login or Register',
    subtitle: 'Patient & Customer Portal',
    description: 'Search specialist doctors, book consultation slots, receive daily queue tokens, and track live wait times.',
    icon: '👥',
    badgeClass: 'role-pill-patient',
    iconClass: 'role-icon-patient',
    cardClass: 'role-card-patient',
    calloutClass: 'callout-patient',
    calloutText: 'Patients and visitors can register or sign in to schedule visits and track live queue positions.',
    portalTab: 'providers',
    btnLabel: 'Patient',
  },
};

// ==============================================================================
// Component: LandingPage (Two-Column Layout: Left Image, Right 3 Role Choices)
// ==============================================================================
function LandingPage({ onSelectRole, currentUser, onGoToPortal, onLogout }) {
  return (
    <div className="landing-layout">
      {/* Left Column: Large Healthcare Illustration Panel */}
      <div className="landing-image-column">
        <div className="landing-image-card">
          <div className="landing-hero-img-wrap">
            <img
              src="/healthcare-hero.svg"
              alt="CareQueue Healthcare & Live Queue Illustration"
              className="landing-hero-img"
            />
          </div>
          <div className="landing-image-caption">
            <h3>CareQueue Smart Healthcare Services</h3>
            <p>
              Digital appointment scheduling with automated daily queue tokens, dynamic waiting time estimation, and live consultation tracking.
            </p>
          </div>
        </div>
      </div>

      {/* Right Column: Three Role Choices */}
      <div className="landing-options-column">
        <div className="landing-options-card">
          <div className="landing-header">
            <span className="hero-tag">Welcome to CareQueue</span>
            <h2 className="landing-title">Select Your Portal</h2>
            <p className="landing-subtitle">
              Choose your role below to log in or register. Each portal provides specialized tools for administrators, healthcare providers, and patients.
            </p>
          </div>

          {currentUser && (
            <div className="signed-in-home-banner">
              <div>
                <p style={{ margin: 0, fontSize: '0.72rem', fontWeight: 800, color: '#0284c7', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  ACTIVE SIGNED-IN SESSION
                </p>
                <h4 style={{ margin: '0.2rem 0', color: '#0f172a' }}>
                  {currentUser.name} ({currentUser.role})
                </h4>
              </div>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button
                  className="btn btn-primary"
                  style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem' }}
                  onClick={onGoToPortal}
                >
                  Enter Portal →
                </button>
                <button
                  className="btn btn-secondary"
                  style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem' }}
                  onClick={onLogout}
                >
                  Sign Out
                </button>
              </div>
            </div>
          )}

          <div className="role-options-list">
            {/* 1. Admin Login or Register */}
            <div
              className="role-option-card role-card-admin"
              tabIndex={0}
              role="button"
              aria-label="Admin Login or Register: Hospital administration, staff credentials, system settings, and analytics"
              onClick={() => onSelectRole('/admin/auth')}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onSelectRole('/admin/auth');
                }
              }}
            >
              <div className="role-option-icon">🛡️</div>
              <div className="role-option-content">
                <div className="role-option-badge-row">
                  <span className="role-option-pill role-pill-admin">1. Administrator</span>
                </div>
                <h3 className="role-option-title">Admin Login or Register</h3>
                <p className="role-option-desc">
                  Hospital administration, specialist staff credentials, overall queue activity, and system metrics.
                </p>
              </div>
              <span className="role-option-arrow">→</span>
            </div>

            {/* 2. Doctor Login or Register */}
            <div
              className="role-option-card role-card-doctor"
              tabIndex={0}
              role="button"
              aria-label="Doctor Login or Register: Consultation schedule, working hours, live patient calling, and clinic queue management"
              onClick={() => onSelectRole('/doctor/auth')}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onSelectRole('/doctor/auth');
                }
              }}
            >
              <div className="role-option-icon">🩺</div>
              <div className="role-option-content">
                <div className="role-option-badge-row">
                  <span className="role-option-pill role-pill-doctor">2. Doctor / Specialist</span>
                </div>
                <h3 className="role-option-title">Doctor Login or Register</h3>
                <p className="role-option-desc">
                  Manage consultation hours, call waiting patients in order, and track daily clinic queues.
                </p>
              </div>
              <span className="role-option-arrow">→</span>
            </div>

            {/* 3. Patient Login or Register */}
            <div
              className="role-option-card role-card-patient"
              tabIndex={0}
              role="button"
              aria-label="Patient Login or Register: Search doctors, book consultation slots, receive daily queue tokens, and track wait times"
              onClick={() => onSelectRole('/patient/auth')}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onSelectRole('/patient/auth');
                }
              }}
            >
              <div className="role-option-icon">👥</div>
              <div className="role-option-content">
                <div className="role-option-badge-row">
                  <span className="role-option-pill role-pill-patient">3. Patient / Visitor</span>
                </div>
                <h3 className="role-option-title">Patient Login or Register</h3>
                <p className="role-option-desc">
                  Search specialist doctors, book consultation slots, receive daily queue tokens, and track wait times.
                </p>
              </div>
              <span className="role-option-arrow">→</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ==============================================================================
// Component: RoleAuthView (Separate Role Authentication Page)
// ==============================================================================
function RoleAuthView({
  config,
  onBack,
  authTab,
  setAuthTab,
  loginEmail,
  setLoginEmail,
  loginPassword,
  setLoginPassword,
  regName,
  setRegName,
  regEmail,
  setRegEmail,
  regPassword,
  setRegPassword,
  isSubmitting,
  authError,
  authSuccess,
  onSubmitLogin,
  onSubmitRegister,
  currentUser,
  onLogout,
  onGoToPortal,
}) {
  return (
    <div className="role-auth-container">
      <div className="role-auth-top-nav">
        <button className="back-to-landing-btn" onClick={onBack}>
          ← Return to Landing Page
        </button>
        <span className={`role-option-pill ${config.badgeClass}`}>
          {config.key === 'PROVIDER' ? 'DOCTOR' : config.key} PORTAL
        </span>
      </div>

      <div className="role-auth-card">
        <div className="role-auth-header">
          <div className={`role-auth-icon-badge ${config.iconClass}`}>
            {config.icon}
          </div>
          <h2 className="role-auth-title">{config.title}</h2>
          <p className="role-auth-subtitle">{config.description}</p>
        </div>

        <div className={`role-notice-callout ${config.calloutClass}`}>
          <span>ℹ️</span>
          <div>
            <strong>Portal Requirement:</strong> {config.calloutText}
          </div>
        </div>

        {authSuccess && <div className="alert alert-success">{authSuccess}</div>}
        {authError && <div className="alert alert-error">{authError}</div>}

        {currentUser ? (
          <div>
            <div className="profile-header">
              <div>
                <h3 className="profile-name">Currently Signed In: {currentUser.name}</h3>
                <p style={{ color: '#64748b', fontSize: '0.9rem' }}>{currentUser.email}</p>
              </div>
              <span className={`role-badge ${currentUser.role === 'ADMIN' ? 'role-badge-admin' : currentUser.role === 'PROVIDER' ? 'role-badge-provider' : 'role-badge-customer'}`}>
                {currentUser.role}
              </span>
            </div>

            <div className="profile-box">
              <div className="detail-row">
                <span className="detail-label">Active Role:</span>
                <span className="detail-value">{currentUser.role}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Portal Selected:</span>
                <span className="detail-value">{config.subtitle}</span>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem' }}>
              <button className="btn btn-primary" onClick={onGoToPortal}>
                Go to {currentUser.role} Dashboard →
              </button>
              <button className="btn btn-danger" onClick={onLogout}>
                Sign Out
              </button>
            </div>
          </div>
        ) : (
          <div>
            <div className="auth-tabs">
              <button
                className={`tab-btn ${authTab === 'login' ? 'active' : ''}`}
                onClick={() => setAuthTab('login')}
              >
                Sign In
              </button>
              <button
                className={`tab-btn ${authTab === 'register' ? 'active' : ''}`}
                onClick={() => setAuthTab('register')}
              >
                Register as {config.btnLabel}
              </button>
            </div>

            {authTab === 'login' ? (
              <form onSubmit={(e) => onSubmitLogin(e, config.key)} className="auth-form">
                <div className="form-group">
                  <label htmlFor="role-login-email" className="form-label">
                    {config.btnLabel} Email Address
                  </label>
                  <input
                    id="role-login-email"
                    type="email"
                    className="form-input"
                    placeholder={`${config.btnLabel.toLowerCase()}@example.com`}
                    value={loginEmail}
                    onChange={(e) => setLoginEmail(e.target.value)}
                    required
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="role-login-password" className="form-label">
                    Password
                  </label>
                  <input
                    id="role-login-password"
                    type="password"
                    className="form-input"
                    placeholder="Enter your account password"
                    value={loginPassword}
                    onChange={(e) => setLoginPassword(e.target.value)}
                    required
                  />
                </div>

                <button
                  type="submit"
                  className="btn btn-primary"
                  style={{ width: '100%', marginTop: '0.75rem', padding: '0.75rem' }}
                  disabled={isSubmitting}
                >
                  {isSubmitting ? 'Authenticating...' : `Sign In as ${config.btnLabel}`}
                </button>
              </form>
            ) : (
              <form onSubmit={(e) => onSubmitRegister(e, config.key)} className="auth-form">
                <div className="form-group">
                  <label htmlFor="role-reg-name" className="form-label">
                    Full Name {config.key === 'PROVIDER' ? '(e.g. Dr. Alex Mercer, MD)' : ''}
                  </label>
                  <input
                    id="role-reg-name"
                    type="text"
                    className="form-input"
                    placeholder={config.key === 'PROVIDER' ? 'Dr. Sarah Jenkins, MD' : 'Full Name'}
                    value={regName}
                    onChange={(e) => setRegName(e.target.value)}
                    required
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="role-reg-email" className="form-label">
                    Email Address
                  </label>
                  <input
                    id="role-reg-email"
                    type="email"
                    className="form-input"
                    placeholder={`${config.btnLabel.toLowerCase()}@example.com`}
                    value={regEmail}
                    onChange={(e) => setRegEmail(e.target.value)}
                    required
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="role-reg-password" className="form-label">
                    Password (minimum 6 characters)
                  </label>
                  <input
                    id="role-reg-password"
                    type="password"
                    className="form-input"
                    placeholder="Create a strong password"
                    value={regPassword}
                    onChange={(e) => setRegPassword(e.target.value)}
                    required
                    minLength={6}
                  />
                </div>

                <button
                  type="submit"
                  className="btn btn-primary"
                  style={{ width: '100%', marginTop: '0.75rem', padding: '0.75rem' }}
                  disabled={isSubmitting}
                >
                  {isSubmitting ? 'Registering...' : `Register as ${config.btnLabel}`}
                </button>
              </form>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

// ==============================================================================
// Component: BookingFlow (Shared Reusable Booking Flow with Self-Booking Guard)
// ==============================================================================
function BookingFlow({
  currentUser,
  providers,
  bookableProviders,
  selectedProvider,
  setSelectedProvider,
  selectedDate,
  setSelectedDate,
  slotData,
  isLoadingSlots,
  slotError,
  selectedSlot,
  setSelectedSlot,
  bookingNotes,
  setBookingNotes,
  isBooking,
  bookingSuccessMsg,
  bookingErrorMsg,
  providerListError,
  isLoadingProviders,
  loadProviders,
  handleConfirmBooking,
  onGoToLogin,
  bannerNotice,
  onReloadSlots,
  lastBookedToken,
  setLastBookedToken,
  onViewMyAppointments,
}) {
  // Live clock: updates automatically every minute (polled every 10s for responsive slot expiration)
  const [liveClock, setLiveClock] = useState(new Date());

  useEffect(() => {
    const timer = setInterval(() => {
      setLiveClock(new Date());
    }, 10000);
    return () => clearInterval(timer);
  }, []);

  // Application / Provider timezone (defaulting to Asia/Kolkata / IST UTC+05:30)
  const appTimezone = slotData?.timezone || APP_TIMEZONE;

  // Format live date & time for display
  const formattedLiveDate = liveClock.toLocaleDateString('en-IN', {
    weekday: 'short',
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    timeZone: appTimezone,
  });

  const formattedLiveTime = liveClock.toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: true,
    timeZone: appTimezone,
  });

  const todayStr = getTodayString(liveClock, appTimezone);

  // Current time in HH:mm in app timezone
  const currentHHMM = (() => {
    try {
      return new Intl.DateTimeFormat('en-GB', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
        timeZone: appTimezone,
      }).format(liveClock);
    } catch (e) {
      return String(liveClock.getHours()).padStart(2, '0') + ':' + String(liveClock.getMinutes()).padStart(2, '0');
    }
  })();

  const isToday = selectedDate === todayStr;
  const isPastDate = selectedDate < todayStr;

  // When the clock's minute rolls over, refresh slot availability from backend
  const currentMinute = liveClock.getMinutes();
  useEffect(() => {
    if (selectedProvider && onReloadSlots) {
      onReloadSlots();
    }
  }, [currentMinute]);

  // If currently selected slot has just passed or date is past, deselect it immediately
  useEffect(() => {
    if (selectedSlot) {
      const slotStart = selectedSlot.startTime || selectedSlot.start;
      if (isPastDate || (isToday && slotStart <= currentHHMM)) {
        setSelectedSlot(null);
      }
    }
  }, [liveClock, selectedDate, selectedSlot, isToday, isPastDate, currentHHMM]);

  // Derived slot statistics
  const allSlots = slotData?.slots || [];
  const bookableSlots = allSlots.filter((slot) => {
    const isBooked = slot.isBooked || slot.booked;
    const slotStart = slot.startTime || slot.start;
    const isPast = isPastDate || (isToday && slotStart <= currentHHMM) || slot.isPast || slot.past;
    return !isBooked && !isPast;
  });
  const bookableCount = bookableSlots.length;

  return (
    <div>
      {bannerNotice && (
        <div className="alert alert-info" style={{ marginBottom: '1.25rem' }}>
          {bannerNotice}
        </div>
      )}

      {/* Booking Confirmation & Generated Token Banner */}
      {lastBookedToken && (
        <div className="token-booked-banner" style={{ marginBottom: '1.25rem' }}>
          <div className="token-banner-icon">🎟️</div>
          <div className="token-banner-body">
            <h3>Appointment Confirmed! Queue Token: #{lastBookedToken.tokenDisplay || ('T-00' + lastBookedToken.tokenNumber)}</h3>
            <p>
              Your appointment is secured for <strong>{lastBookedToken.appointmentDate}</strong> at <strong>{lastBookedToken.startTime}</strong> with <strong>{lastBookedToken.providerName}</strong>.
              Queue status is <span className="status-badge status-badge-waiting">{lastBookedToken.tokenStatus || 'WAITING'}</span>.
            </p>
            {onViewMyAppointments && (
              <button
                type="button"
                className="btn btn-secondary"
                style={{ marginTop: '0.5rem', fontSize: '0.8rem', padding: '0.3rem 0.75rem' }}
                onClick={onViewMyAppointments}
              >
                View in My Appointments ➔
              </button>
            )}
          </div>
          <button className="btn-close-banner" onClick={() => setLastBookedToken && setLastBookedToken(null)} title="Dismiss">✕</button>
        </div>
      )}

      {bookingSuccessMsg && <div className="alert alert-success">{bookingSuccessMsg}</div>}
      {bookingErrorMsg && <div className="alert alert-error">{bookingErrorMsg}</div>}
      {providerListError && <div className="alert alert-error">{providerListError}</div>}

      {isLoadingProviders ? (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Loading available doctors...</p>
        </div>
      ) : providers.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">🩺</div>
          <h3>No Active Doctors Found</h3>
          <p>
            No healthcare providers are active in the clinic directory.
          </p>
        </div>
      ) : bookableProviders.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">🩺</div>
          <h3>No Other Doctors Available</h3>
          <p>
            You are currently registered as the only active doctor in the system. As a doctor or administrator, you cannot book an appointment with yourself. Another doctor must be registered in the clinic before you can book a consultation.
          </p>
        </div>
      ) : (
        <div>
          {/* Doctor Directory Grid */}
          <div className="providers-grid">
            {bookableProviders.map((p) => {
              const isSelected = selectedProvider && selectedProvider.id === p.id;
              return (
                <div
                  key={p.id}
                  className={`provider-card ${isSelected ? 'selected' : ''}`}
                  onClick={() => setSelectedProvider(p)}
                >
                  <div className="provider-card-header">
                    <div className="provider-avatar">👨‍⚕️</div>
                    <div>
                      <h3 className="provider-name">{p.displayName}</h3>
                      <span className="provider-specialty">{p.serviceDescription || 'General Medicine'}</span>
                    </div>
                  </div>

                  <div className="provider-schedule-info">
                    <div className="schedule-row">
                      <span className="schedule-label">Working Days:</span>
                      <span className="schedule-value">
                        {p.workingDays && p.workingDays.length > 0
                          ? p.workingDays.map((d) => d.slice(0, 3)).join(', ')
                          : 'None configured'}
                      </span>
                    </div>
                    <div className="schedule-row">
                      <span className="schedule-label">Hours:</span>
                      <span className="schedule-value">{p.openTime} – {p.closeTime}</span>
                    </div>
                    <div className="schedule-row">
                      <span className="schedule-label">Slot Duration:</span>
                      <span className="schedule-value">{p.slotDurationMinutes} mins</span>
                    </div>
                  </div>

                  <button
                    className={`btn ${isSelected ? 'btn-primary' : 'btn-secondary'}`}
                    style={{ width: '100%', marginTop: '0.75rem', fontSize: '0.85rem' }}
                    onClick={(e) => {
                      e.stopPropagation();
                      setSelectedProvider(p);
                    }}
                  >
                    {isSelected ? '✓ Selected' : 'Choose Doctor'}
                  </button>
                </div>
              );
            })}
          </div>

          {/* Slot Picker & Booking Form */}
          {selectedProvider && (
            <div className="slots-panel">
              <div className="slots-panel-header">
                <div>
                  <h3 className="slots-title">
                    Available Slots for {selectedProvider.displayName}
                  </h3>
                  <p className="slots-subtitle">
                    Consultation Window: {selectedProvider.openTime} to {selectedProvider.closeTime} ({selectedProvider.slotDurationMinutes} min sessions)
                  </p>
                </div>

                <div className="date-picker-group">
                  <label htmlFor="slotDate" className="date-picker-label">Date:</label>
                  <input
                    id="slotDate"
                    type="date"
                    className="form-input date-input"
                    value={selectedDate}
                    min={getTodayString(liveClock, appTimezone)}
                    onChange={(e) => setSelectedDate(e.target.value)}
                  />
                </div>
              </div>

              {/* Live Current Time & Timezone Indicator */}
              <div className="live-clock-card">
                <div className="live-clock-badge">
                  <span className="live-pulse-dot"></span>
                  <span className="live-label">LIVE CLINIC TIME</span>
                </div>
                <div className="live-clock-details">
                  <span className="live-clock-time">{formattedLiveTime}</span>
                  <span className="live-clock-separator">•</span>
                  <span className="live-clock-date">{formattedLiveDate}</span>
                  <span className="live-clock-tz">Timezone: <strong>{appTimezone} (IST)</strong></span>
                </div>
                <span className="live-clock-note">⚡ Availability updates live automatically every minute</span>
              </div>

              {/* Interactive Quick Date Options Bar */}
              <div className="date-options-wrapper">
                <span className="date-options-label">Select Date:</span>
                <div className="date-options-scroll">
                  {getUpcomingDates(7, appTimezone).map((d) => {
                    const isSelected = selectedDate === d.dateString;
                    const isDocWorking = selectedProvider.workingDays && selectedProvider.workingDays.some(
                      (w) => w.toUpperCase() === d.fullDayOfWeek
                    );

                    return (
                      <button
                        key={d.dateString}
                        type="button"
                        className={`date-option-pill ${isSelected ? 'active' : ''} ${!isDocWorking ? 'closed-day' : ''}`}
                        onClick={() => setSelectedDate(d.dateString)}
                        title={`${d.dateString} (${d.fullDayOfWeek}) - ${isDocWorking ? 'Doctor is in clinic' : 'Doctor is closed'}`}
                      >
                        <span className="date-option-day">{d.label}</span>
                        <span className="date-option-status">
                          {isDocWorking ? '🟢 Open' : '⚪ Closed'}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {slotError && <div className="alert alert-error">{slotError}</div>}

              {isLoadingSlots ? (
                <div className="loading-state">
                  <div className="spinner"></div>
                  <p>Fetching slots for {selectedDate}...</p>
                </div>
              ) : slotData ? (
                <div>
                  {!(slotData.workingDay !== undefined ? slotData.workingDay : slotData.isWorkingDay) ? (
                    <div className="alert alert-info">
                      🗓️ <strong>Doctor Not in Clinic:</strong> {selectedProvider.displayName} does not have consultation hours on {slotData.dayOfWeek}s. Please pick an open date from the options above when {selectedProvider.displayName} is in clinic.
                    </div>
                  ) : allSlots.length === 0 ? (
                    <div className="alert alert-info">
                      No slots generated for {selectedDate}.
                    </div>
                  ) : (
                    <div>
                      {/* Slots Header Statistics */}
                      <div className="slots-meta" style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', alignItems: 'center', marginBottom: '0.85rem', fontSize: '0.875rem' }}>
                        <span>Date: <strong>{slotData.date} ({slotData.dayOfWeek})</strong></span>
                        <span>Total Slots: <strong>{allSlots.length}</strong></span>
                        <span>Bookable Now: <strong style={{ color: bookableCount > 0 ? '#16a34a' : '#dc2626' }}>{bookableCount}</strong></span>
                        {isToday && (
                          <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                            (Slots starting before {currentHHMM} are closed)
                          </span>
                        )}
                      </div>

                      <div className="slots-grid">
                        {allSlots.map((slot) => {
                          const slotStartVal = slot.startTime || slot.start;
                          const isSlotSelected = selectedSlot && (selectedSlot.startTime === slotStartVal || selectedSlot.start === slotStartVal);
                          const isBooked = slot.isBooked || slot.booked;
                          const isPast = isPastDate || (isToday && slotStartVal <= currentHHMM) || slot.isPast || slot.past;
                          const isUnavailable = isBooked || isPast;

                          return (
                            <button
                              key={slotStartVal}
                              type="button"
                              className={`slot-chip ${isBooked ? 'booked' : ''} ${isPast ? 'past-slot' : ''} ${isSlotSelected ? 'selected' : ''}`}
                              disabled={isUnavailable}
                              onClick={() => {
                                if (!isUnavailable) {
                                  setSelectedSlot(slot);
                                }
                              }}
                              title={
                                isBooked
                                  ? 'This slot is already booked'
                                  : isPast
                                  ? `This slot has already passed (live time: ${formattedLiveTime})`
                                  : `Book consultation slot at ${slot.formatted}`
                              }
                            >
                              <span className="slot-time">{slot.formatted}</span>
                              <span className={`slot-badge ${isBooked ? 'badge-booked' : isPast ? 'badge-past' : 'badge-open'}`}>
                                {isBooked ? '🔒 Booked' : isPast ? '⌛ Passed' : '🟢 Available'}
                              </span>
                            </button>
                          );
                        })}
                      </div>

                      {/* Clear Message If No Bookable Slots Remain */}
                      {bookableCount === 0 && (
                        <div className="alert alert-warning" style={{ marginTop: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
                          <span style={{ fontSize: '1.25rem' }}>⚠️</span>
                          <div>
                            <strong>No Bookable Slots Remaining for {selectedDate} ({slotData.dayOfWeek}):</strong>{' '}
                            {isToday
                              ? 'All consultation slots for today have already passed the live current time or are fully booked. Please select an upcoming date from the options above.'
                              : 'All consultation slots for this date are fully booked. Please select another date from the options above.'}
                          </div>
                        </div>
                      )}

                      {/* Booking Confirmation Box */}
                      {selectedSlot && (
                        <div className="booking-confirm-card">
                          <h4 style={{ margin: '0 0 0.5rem 0', color: '#1e293b' }}>
                            Confirm Appointment with {selectedProvider.displayName}
                          </h4>
                          <p style={{ margin: '0 0 1rem 0', color: '#475569', fontSize: '0.9rem' }}>
                            <strong>Date & Time:</strong> {selectedDate} ({slotData.dayOfWeek}) from {selectedSlot.formatted}
                          </p>

                          <div className="form-group">
                            <label className="form-label" htmlFor="bookingNotes">
                              Reason for Consultation / Medical Notes (Optional):
                            </label>
                            <textarea
                              id="bookingNotes"
                              className="form-input"
                              rows={2}
                              placeholder="e.g. Follow-up consultation, personal health checkup, fever"
                              value={bookingNotes}
                              onChange={(e) => setBookingNotes(e.target.value)}
                            />
                          </div>

                          {currentUser ? (
                            <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
                              <button
                                className="btn btn-primary"
                                onClick={handleConfirmBooking}
                                disabled={isBooking}
                              >
                                {isBooking ? 'Securing Slot...' : '✓ Confirm & Book Appointment'}
                              </button>
                              <button
                                className="btn btn-secondary"
                                onClick={() => setSelectedSlot(null)}
                              >
                                Cancel
                              </button>
                              <span style={{ fontSize: '0.825rem', color: '#475569' }}>
                                Booking as patient: <strong>{currentUser.name}</strong> ({currentUser.email}) • <span className={`role-badge ${currentUser.role === 'ADMIN' ? 'role-badge-admin' : currentUser.role === 'PROVIDER' ? 'role-badge-provider' : 'role-badge-customer'}`}>{currentUser.role}</span>
                              </span>
                            </div>
                          ) : (
                            <div className="alert alert-info" style={{ marginTop: '1rem' }}>
                              🔒 <strong>Login Required:</strong> Please sign in to finalize your booking.
                              <div style={{ marginTop: '0.5rem' }}>
                                <button
                                  className="btn btn-primary"
                                  style={{ fontSize: '0.85rem', padding: '0.4rem 0.8rem' }}
                                  onClick={onGoToLogin}
                                >
                                  Go to Login
                                </button>
                              </div>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              ) : null}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function App() {
  // --- Backend Health Status State (Phase 1) ---
  const [healthStatus, setHealthStatus] = useState('checking'); // 'checking' | 'online' | 'offline'
  const [healthData, setHealthData] = useState(null);
  const [healthError, setHealthError] = useState('');
  const [lastChecked, setLastChecked] = useState(null);

  // --- Authentication State (Phase 2) ---
  const [currentUser, setCurrentUser] = useState(null);
  const [authTab, setAuthTab] = useState('login'); // 'login' | 'register'
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [authError, setAuthError] = useState('');
  const [authSuccess, setAuthSuccess] = useState('');

  // Form Fields for Auth
  const [regName, setRegName] = useState('');
  const [regEmail, setRegEmail] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');

  // --- Active Tab State ---
  const [activeTab, setActiveTab] = useState('providers'); // 'landing' | 'providers' | 'my-appointments' | 'my-schedule' | 'auth'

  // --- Browser Path / Routing State ---
  const [currentPath, setCurrentPath] = useState(window.location.pathname || '/');

  useEffect(() => {
    const handlePopState = () => {
      setCurrentPath(window.location.pathname || '/');
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const navigateTo = (path) => {
    window.history.pushState({}, '', path);
    setCurrentPath(path);
    setAuthError('');
    setAuthSuccess('');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const getRoleConfigForPath = (path) => {
    if (path === '/admin/auth') return ROLE_CONFIG.ADMIN;
    if (path === '/doctor/auth') return ROLE_CONFIG.PROVIDER;
    if (path === '/patient/auth') return ROLE_CONFIG.CUSTOMER;
    return null;
  };

  const activeRoleConfig = getRoleConfigForPath(currentPath);

  // --- Provider & Availability State (Phase 3) ---
  const [providers, setProviders] = useState([]);
  const [isLoadingProviders, setIsLoadingProviders] = useState(false);
  const [providerListError, setProviderListError] = useState('');

  // Slot Viewer & Selection
  const [selectedProvider, setSelectedProvider] = useState(null);
  const [selectedDate, setSelectedDate] = useState(getTodayString());
  const [slotData, setSlotData] = useState(null);
  const [isLoadingSlots, setIsLoadingSlots] = useState(false);
  const [slotError, setSlotError] = useState('');
  const [selectedSlot, setSelectedSlot] = useState(null);

  // Bookable providers: strictly exclude currently logged-in user by user ID (Admins/Doctors cannot book with themselves)
  const bookableProviders = providers.filter(
    (p) => !currentUser || p.userId !== currentUser.id
  );

  // Doctor Dashboard Subtabs: 'queue' (Daily Patient Queue) vs 'book' (Book With Another Doctor)
  const [doctorQueueTab, setDoctorQueueTab] = useState('queue');

  // --- Booking State (Phase 4) ---
  const [bookingNotes, setBookingNotes] = useState('');
  const [isBooking, setIsBooking] = useState(false);
  const [bookingSuccessMsg, setBookingSuccessMsg] = useState('');
  const [bookingErrorMsg, setBookingErrorMsg] = useState('');

  // --- My Appointments State (Phase 4) ---
  const [myAppointments, setMyAppointments] = useState([]);
  const [isLoadingAppointments, setIsLoadingAppointments] = useState(false);
  const [appointmentsError, setAppointmentsError] = useState('');
  const [cancellingId, setCancellingId] = useState(null);
  const [cancelSuccessMsg, setCancelSuccessMsg] = useState('');
  const [cancelErrorMsg, setCancelErrorMsg] = useState('');

  // History view modal/drawer state
  const [historyModalApptId, setHistoryModalApptId] = useState(null);
  const [appointmentHistory, setAppointmentHistory] = useState([]);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);

  // Appointment Edit / Reschedule State
  const [editingAppt, setEditingAppt] = useState(null);
  const [editNotes, setEditNotes] = useState('');
  const [editDate, setEditDate] = useState('');
  const [editSlot, setEditSlot] = useState(null);
  const [editSlotsList, setEditSlotsList] = useState([]);
  const [isLoadingEditSlots, setIsLoadingEditSlots] = useState(false);
  const [isSavingEdit, setIsSavingEdit] = useState(false);
  const [editError, setEditError] = useState('');
  const [editSuccessMsg, setEditSuccessMsg] = useState('');

  // --- Provider Clinic Queue State (Phase 5) ---
  const [lastBookedToken, setLastBookedToken] = useState(null);
  const [queueDate, setQueueDate] = useState(getTodayString());
  const [providerQueue, setProviderQueue] = useState([]);
  const [isLoadingQueue, setIsLoadingQueue] = useState(false);
  const [queueError, setQueueError] = useState('');
  const [queueSuccessMsg, setQueueSuccessMsg] = useState('');
  const [isCallingNext, setIsCallingNext] = useState(false);
  const [updatingTokenId, setUpdatingTokenId] = useState(null);

  // --- Customer Queue Tracking State (Phase 6) ---
  const [myActiveToken, setMyActiveToken] = useState(null);
  const [myQueueTokens, setMyQueueTokens] = useState([]);
  const [isLoadingMyToken, setIsLoadingMyToken] = useState(false);
  const [myTokenError, setMyTokenError] = useState('');
  const [lastMyTokenUpdated, setLastMyTokenUpdated] = useState(null);
  const [queueNotification, setQueueNotification] = useState(null);
  const lastNotifiedRef = useRef({ tokenId: null, status: null, approachingSent: false });

  // --- Admin Dashboard State (Phase 6) ---
  const [adminStats, setAdminStats] = useState(null);
  const [adminUsers, setAdminUsers] = useState([]);
  const [adminProviders, setAdminProviders] = useState([]);
  const [adminAppointments, setAdminAppointments] = useState([]);
  const [adminQueue, setAdminQueue] = useState([]);
  const [adminAnalytics, setAdminAnalytics] = useState(null);
  const [analyticsStartDate, setAnalyticsStartDate] = useState('');
  const [analyticsEndDate, setAnalyticsEndDate] = useState('');
  const [adminSubTab, setAdminSubTab] = useState('overview'); // 'overview' | 'analytics' | 'users' | 'providers' | 'appointments' | 'book'
  const [isLoadingAdmin, setIsLoadingAdmin] = useState(false);
  const [adminError, setAdminError] = useState('');
  const [adminSuccessMsg, setAdminSuccessMsg] = useState('');
  const [lastAdminRefresh, setLastAdminRefresh] = useState(null);

  // Search, Filter & Pagination State for Admin Subtabs
  const [userSearchQuery, setUserSearchQuery] = useState('');
  const [userRoleFilter, setUserRoleFilter] = useState('ALL');
  const [userPage, setUserPage] = useState(1);

  const [providerSearchQuery, setProviderSearchQuery] = useState('');
  const [providerStatusFilter, setProviderStatusFilter] = useState('ALL');
  const [providerPage, setProviderPage] = useState(1);

  const [apptSearchQuery, setApptSearchQuery] = useState('');
  const [apptStatusFilter, setApptStatusFilter] = useState('ALL');
  const [apptPage, setApptPage] = useState(1);

  const [queueSearchQuery, setQueueSearchQuery] = useState('');
  const [queueStatusFilter, setQueueStatusFilter] = useState('ALL');
  const [queuePage, setQueuePage] = useState(1);

  // --- Provider Management Form (Phase 3) ---
  const [providerForm, setProviderForm] = useState({
    displayName: '',
    serviceDescription: '',
    active: true,
    workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
    openTime: '09:00',
    closeTime: '17:00',
    slotDurationMinutes: 30,
  });
  const [isLoadingProfile, setIsLoadingProfile] = useState(false);
  const [isSavingProfile, setIsSavingProfile] = useState(false);
  const [profileMsg, setProfileMsg] = useState({ type: '', text: '' });

  // 1. Check backend health
  const testConnection = async () => {
    setHealthStatus('checking');
    setHealthError('');
    const result = await checkBackendHealth();
    setLastChecked(new Date().toLocaleTimeString());

    if (result.isOnline) {
      setHealthStatus('online');
      setHealthData(result.data);
    } else {
      setHealthStatus('offline');
      setHealthData(null);
      setHealthError(result.error);
    }
  };

  // 2. Restore authenticated session
  const loadUserSession = async () => {
    const session = await fetchCurrentUser();
    if (session.authenticated && session.user) {
      setCurrentUser(session.user);
      loadCustomerAppointments();
    } else {
      setCurrentUser(null);
      setMyAppointments([]);
    }
  };

  // 3. Load active providers directory
  const loadProviders = async () => {
    setIsLoadingProviders(true);
    setProviderListError('');
    const result = await fetchActiveProviders();
    setIsLoadingProviders(false);

    if (result.success) {
      setProviders(result.data);
      setLastChecked(new Date().toLocaleTimeString());
      const bookable = result.data.filter((p) => !currentUser || p.userId !== currentUser.id);
      if (!selectedProvider || (currentUser && selectedProvider.userId === currentUser.id)) {
        setSelectedProvider(bookable.length > 0 ? bookable[0] : null);
      }
    } else {
      setProviderListError(result.error);
    }
  };

  // Synchronize selectedProvider so it never points to the currently logged-in doctor
  useEffect(() => {
    if (selectedProvider && currentUser && selectedProvider.userId === currentUser.id) {
      const bookable = providers.filter((p) => p.userId !== currentUser.id);
      setSelectedProvider(bookable.length > 0 ? bookable[0] : null);
    }
  }, [currentUser, providers]);

  // 4. Load slots when provider or date changes
  const loadSlots = async (providerId, date) => {
    if (!providerId || !date) return;
    setIsLoadingSlots(true);
    setSlotError('');
    setSelectedSlot(null);
    setBookingSuccessMsg('');
    setBookingErrorMsg('');

    const result = await fetchProviderSlots(providerId, date);
    setIsLoadingSlots(false);

    if (result.success) {
      setSlotData(result.data);
    } else {
      setSlotError(result.error);
      setSlotData(null);
    }
  };

  // 5. Load appointments for current customer
  const loadCustomerAppointments = async () => {
    setIsLoadingAppointments(true);
    setAppointmentsError('');
    const result = await fetchMyAppointments();
    setIsLoadingAppointments(false);

    if (result.success && Array.isArray(result.data)) {
      setMyAppointments(result.data);
    } else {
      setMyAppointments([]);
      if (result.error && !result.error.toLowerCase().includes('token') && !result.error.toLowerCase().includes('login')) {
        setAppointmentsError(result.error);
      }
    }
  };

  // 6. Load provider profile for doctor
  const loadProviderProfile = async () => {
    if (!currentUser || (currentUser.role !== 'PROVIDER' && currentUser.role !== 'ADMIN')) return;
    setIsLoadingProfile(true);
    setProfileMsg({ type: '', text: '' });

    const result = await fetchMyProviderProfile();
    setIsLoadingProfile(false);

    if (result.success && result.data) {
      const p = result.data;
      setProviderForm({
        displayName: p.displayName || '',
        serviceDescription: p.serviceDescription || '',
        active: p.active !== undefined ? p.active : true,
        workingDays: p.workingDays || ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
        openTime: p.openTime || '09:00',
        closeTime: p.closeTime || '17:00',
        slotDurationMinutes: p.slotDurationMinutes || 30,
      });
    } else {
      setProfileMsg({ type: 'error', text: result.error || 'Failed to load provider profile' });
    }
  };

  // 7. Load clinic queue for provider
  const loadQueue = async (targetDate) => {
    if (!currentUser || (currentUser.role !== 'PROVIDER' && currentUser.role !== 'ADMIN')) return;
    setIsLoadingQueue(true);
    setQueueError('');
    const d = targetDate || queueDate;
    const result = await fetchProviderQueue(d);
    setIsLoadingQueue(false);

    if (result.success && Array.isArray(result.data)) {
      setProviderQueue(result.data);
    } else {
      setProviderQueue([]);
      setQueueError(result.error || 'Failed to load clinic queue');
    }
  };

  // 8. Call Next Waiting Patient
  const handleCallNext = async () => {
    setIsCallingNext(true);
    setQueueError('');
    setQueueSuccessMsg('');

    const result = await callNextQueueToken(queueDate);
    setIsCallingNext(false);

    if (result.success && result.data) {
      setQueueSuccessMsg(`📢 Called patient ${result.data.customerName || ''} with Token #${result.data.tokenDisplay}!`);
      loadQueue(queueDate);
    } else {
      setQueueError(result.error || 'No waiting patients found to call');
    }
  };

  // 9. Update Token Status
  const handleUpdateTokenStatus = async (tokenId, newStatus, defaultNote = '') => {
    setUpdatingTokenId(tokenId);
    setQueueError('');
    setQueueSuccessMsg('');

    const result = await updateQueueTokenStatus(tokenId, newStatus, defaultNote);
    setUpdatingTokenId(null);

    if (result.success && result.data) {
      setQueueSuccessMsg(`Updated Token #${result.data.tokenDisplay} status to ${newStatus}.`);
      loadQueue(queueDate);
    } else {
      setQueueError(result.error || `Failed to update token status to ${newStatus}`);
    }
  };

  // Initial setup on mount
  useEffect(() => {
    testConnection();
    loadUserSession();
    loadProviders();
  }, []);

  // Reload slots whenever the chosen provider or date changes
  useEffect(() => {
    if (selectedProvider) {
      loadSlots(selectedProvider.id, selectedDate);
    }
  }, [selectedProvider, selectedDate]);

  // Load appointments when switching to appointments tab
  useEffect(() => {
    if (activeTab === 'my-appointments' && currentUser) {
      loadCustomerAppointments();
    }
  }, [activeTab, currentUser]);

  // Load provider settings when entering schedule tab
  useEffect(() => {
    if (activeTab === 'my-schedule' && currentUser && (currentUser.role === 'PROVIDER' || currentUser.role === 'ADMIN')) {
      loadProviderProfile();
    }
  }, [activeTab, currentUser]);

  // Load clinic queue when entering queue tab or date changes
  useEffect(() => {
    if (activeTab === 'clinic-queue' && currentUser && (currentUser.role === 'PROVIDER' || currentUser.role === 'ADMIN')) {
      loadQueue(queueDate);
    }
  }, [activeTab, queueDate, currentUser]);

  // 10. Load Customer Queue Tracking Details (Phase 6)
  const loadMyQueueToken = async (silent = false) => {
    if (!currentUser) return;
    if (!silent) setIsLoadingMyToken(true);
    setMyTokenError('');

    const [activeRes, allRes] = await Promise.all([
      fetchMyActiveQueueToken(),
      fetchMyQueueTokens(),
    ]);

    if (!silent) setIsLoadingMyToken(false);
    setLastMyTokenUpdated(new Date().toLocaleTimeString());

    if (activeRes.success && activeRes.data) {
      const token = activeRes.data;
      setMyActiveToken(token);

      // Track notifications to prevent duplicate alerts across polling cycles
      if (lastNotifiedRef.current.tokenId !== token.tokenId) {
        lastNotifiedRef.current = {
          tokenId: token.tokenId,
          status: token.status,
          approachingSent: false,
        };
      }

      // In-app notification 1: Customer's turn is called
      if (token.status === 'CALLED' && lastNotifiedRef.current.status !== 'CALLED') {
        lastNotifiedRef.current.status = 'CALLED';
        setQueueNotification({
          type: 'called',
          title: '🚨 YOUR TURN IS CALLED!',
          message: `Token #${token.tokenDisplay} is being called now by ${token.providerName}. Please proceed directly to the consultation room!`,
        });
      }
      // In-app notification 2: Customer's turn is approaching (2 or fewer ahead)
      else if (
        token.status === 'WAITING' &&
        token.peopleAhead <= QUEUE_APPROACHING_THRESHOLD &&
        !lastNotifiedRef.current.approachingSent
      ) {
        lastNotifiedRef.current.approachingSent = true;
        lastNotifiedRef.current.status = token.status;
        setQueueNotification({
          type: 'approaching',
          title: '🔔 YOUR TURN IS APPROACHING!',
          message: `You are #${token.queuePosition || (token.peopleAhead + 1)} in line (${token.peopleAhead} person${token.peopleAhead === 1 ? '' : 's'} ahead). Please stay near ${token.providerName}'s waiting area.`,
        });
      } else {
        lastNotifiedRef.current.status = token.status;
      }
    } else {
      setMyActiveToken(null);
      if (activeRes.error && !activeRes.error.includes('No queue token')) {
        setMyTokenError(activeRes.error);
      }
    }

    if (allRes.success && Array.isArray(allRes.data)) {
      setMyQueueTokens(allRes.data);
    }
  };

  // 11. Load Admin Dashboard Data (Phase 6)
  const loadAdminDashboard = async (start = analyticsStartDate, end = analyticsEndDate) => {
    if (!currentUser || currentUser.role !== 'ADMIN') return;
    setIsLoadingAdmin(true);
    setAdminError('');

    const [statsRes, usersRes, provsRes, apptsRes, queueRes, analyticsRes] = await Promise.all([
      fetchAdminStats(),
      fetchAdminUsers(),
      fetchAdminProviders(),
      fetchAdminAppointments(),
      fetchAdminQueue(),
      fetchAdminAnalytics(start, end),
    ]);

    setIsLoadingAdmin(false);
    setLastAdminRefresh(new Date().toLocaleTimeString());

    if (statsRes.success) setAdminStats(statsRes.data);
    else setAdminError(statsRes.error);

    if (usersRes.success) setAdminUsers(usersRes.data);
    if (provsRes.success) setAdminProviders(provsRes.data);
    if (apptsRes.success) setAdminAppointments(apptsRes.data);
    if (queueRes.success) setAdminQueue(queueRes.data);
    if (analyticsRes.success) setAdminAnalytics(analyticsRes.data);
  };

  // Admin Management Actions
  const handleUpdateUserRoleAction = async (userId, newRole) => {
    setAdminError('');
    setAdminSuccessMsg('');
    const res = await updateAdminUserRole(userId, newRole);
    if (res.success) {
      setAdminSuccessMsg(`Updated User #${userId} role to ${newRole}`);
      setAdminUsers((prev) =>
        prev.map((u) => (u.id === userId ? { ...u, role: newRole } : u))
      );
    } else {
      setAdminError(res.error);
    }
  };

  const handleToggleProviderAction = async (providerId) => {
    setAdminError('');
    setAdminSuccessMsg('');
    const res = await toggleAdminProviderActive(providerId);
    if (res.success) {
      const updated = res.data;
      setAdminSuccessMsg(`Doctor #${providerId} is now ${updated.active ? 'ACTIVE' : 'DEACTIVATED'}`);
      setAdminProviders((prev) =>
        prev.map((p) => (p.id === providerId ? { ...p, active: updated.active } : p))
      );
      loadProviders();
    } else {
      setAdminError(res.error);
    }
  };

  const handleApplyAnalyticsFilter = async (e) => {
    if (e) e.preventDefault();
    await loadAdminDashboard(analyticsStartDate, analyticsEndDate);
  };

  const handleQuickAnalyticsPreset = async (days) => {
    const end = getTodayString();
    let start = '';
    if (days > 0) {
      const d = new Date();
      d.setDate(d.getDate() - days);
      start = getTodayString(d);
    }
    setAnalyticsStartDate(start);
    setAnalyticsEndDate(end);
    await loadAdminDashboard(start, end);
  };

  const handleAdminConfirmAppt = async (apptId) => {
    setAdminError('');
    setAdminSuccessMsg('');
    const res = await confirmAppointment(apptId);
    if (res.success) {
      setAdminSuccessMsg(`Appointment #${apptId} confirmed successfully.`);
      loadAdminDashboard();
    } else {
      setAdminError(res.error);
    }
  };

  const handleAdminCompleteAppt = async (apptId) => {
    setAdminError('');
    setAdminSuccessMsg('');
    const res = await completeAppointment(apptId);
    if (res.success) {
      setAdminSuccessMsg(`Appointment #${apptId} marked as completed.`);
      loadAdminDashboard();
    } else {
      setAdminError(res.error);
    }
  };

  const handleAdminCancelAppt = async (apptId) => {
    const reason = prompt('Please enter cancellation reason (optional):', 'Administrative adjustment');
    if (reason === null) return;
    setAdminError('');
    setAdminSuccessMsg('');
    const res = await cancelAppointment(apptId, reason);
    if (res.success) {
      setAdminSuccessMsg(`Appointment #${apptId} has been cancelled.`);
      loadAdminDashboard();
    } else {
      setAdminError(res.error);
    }
  };

  const handleAdminUpdateTokenStatus = async (tokenId, newStatus) => {
    setAdminError('');
    setAdminSuccessMsg('');
    const res = await updateQueueTokenStatus(tokenId, newStatus, 'Status updated by clinic administrator');
    if (res.success) {
      setAdminSuccessMsg(`Token #${res.data?.tokenDisplay || tokenId} status updated to ${newStatus}.`);
      loadAdminDashboard();
    } else {
      setAdminError(res.error);
    }
  };

  // Polling Effect for Customer Queue Tracking (every 10 seconds while active)
  useEffect(() => {
    if (activeTab === 'queue-tracker' && currentUser) {
      // If the customer's active token has reached a terminal status, stop polling
      if (myActiveToken && isTerminalQueueStatus(myActiveToken.status)) {
        return;
      }

      loadMyQueueToken(false);
      const pollTimer = setInterval(() => {
        loadMyQueueToken(true);
      }, 10000); // 10 second interval
      return () => clearInterval(pollTimer);
    }
  }, [activeTab, currentUser, myActiveToken?.status]);

  // Admin Dashboard Loading Effect
  useEffect(() => {
    if (activeTab === 'admin-dashboard' && currentUser && currentUser.role === 'ADMIN') {
      loadAdminDashboard();
    }
  }, [activeTab, currentUser]);

  // Handle role-specific login with strict role verification before navigating to role area
  const handleRoleLogin = async (e, expectedRole) => {
    e.preventDefault();
    setAuthError('');
    setAuthSuccess('');

    if (!loginEmail.trim() || !loginPassword) {
      setAuthError('Please enter both email and password');
      return;
    }

    setIsSubmitting(true);
    const result = await loginUser(loginEmail.trim(), loginPassword);
    setIsSubmitting(false);

    if (result.success) {
      const returnedUser = result.data.user;

      // Strict role verification: verify user possesses the required role for this portal
      if (expectedRole === 'ADMIN' && returnedUser.role !== 'ADMIN') {
        clearStoredToken();
        setAuthError(`Access Denied: Your account role is "${returnedUser.role}", but Administrator credentials are required for the Admin portal. Please use the Patient or Doctor login.`);
        return;
      }

      if (expectedRole === 'PROVIDER' && returnedUser.role !== 'PROVIDER') {
        clearStoredToken();
        setAuthError(`Access Denied: Your account role is "${returnedUser.role}", but Doctor credentials are required for the Doctor portal. Please use the Patient login.`);
        return;
      }

      saveToken(result.data.token);
      setCurrentUser(returnedUser);
      setAuthSuccess(`Welcome back, ${returnedUser.name}! Successfully signed in as ${returnedUser.role}.`);
      setLoginEmail('');
      setLoginPassword('');

      // Navigate directly to role-specific portal area
      if (returnedUser.role === 'ADMIN') {
        setActiveTab('admin-dashboard');
      } else if (returnedUser.role === 'PROVIDER') {
        setActiveTab('clinic-queue');
      } else {
        setActiveTab('providers');
      }

      loadProviders();
      loadCustomerAppointments();
      navigateTo('/');
    } else {
      setAuthError(result.error);
    }
  };

  // Handle role-specific registration
  const handleRoleRegister = async (e, targetRole) => {
    e.preventDefault();
    setAuthError('');
    setAuthSuccess('');

    if (!regName.trim() || regName.trim().length < 2) {
      setAuthError('Please enter your full name (at least 2 characters)');
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!regEmail.trim() || !emailRegex.test(regEmail.trim())) {
      setAuthError('Please provide a valid email address');
      return;
    }

    if (regPassword.length < 6) {
      setAuthError('Password must be at least 6 characters long');
      return;
    }

    setIsSubmitting(true);
    const result = await registerUser(regName.trim(), regEmail.trim(), regPassword, targetRole);
    setIsSubmitting(false);

    if (result.success) {
      const returnedUser = result.data.user;

      // Verify role returned by backend matches requested role
      if (targetRole && returnedUser.role !== targetRole) {
        setAuthError(`Account created, but backend assigned role "${returnedUser.role}" instead of requested "${targetRole}".`);
        return;
      }

      saveToken(result.data.token);
      setCurrentUser(returnedUser);
      setAuthSuccess(`Account created successfully! Welcome to CareQueue as ${returnedUser.role}.`);
      setRegName('');
      setRegEmail('');
      setRegPassword('');

      // Navigate directly to role-specific portal area
      if (returnedUser.role === 'ADMIN') {
        setActiveTab('admin-dashboard');
      } else if (returnedUser.role === 'PROVIDER') {
        setActiveTab('clinic-queue');
      } else {
        setActiveTab('providers');
      }

      loadProviders();
      loadCustomerAppointments();
      navigateTo('/');
    } else {
      setAuthError(result.error);
    }
  };

  // Handle registration
  const handleRegister = async (e) => {
    handleRoleRegister(e, 'CUSTOMER');
  };

  // Handle login
  const handleLogin = async (e) => {
    handleRoleLogin(e, null);
  };

  // Handle logout
  const handleLogout = () => {
    clearStoredToken();
    setCurrentUser(null);
    setMyAppointments([]);
    setAuthSuccess('You have been logged out.');
    setAuthError('');
    setActiveTab('landing');
    navigateTo('/');
  };

  // Handle Booking Confirmation (Phase 4)
  const handleConfirmBooking = async () => {
    if (!currentUser) {
      setBookingErrorMsg('Please log in or register an account before booking.');
      return;
    }
    if (!selectedProvider || !selectedSlot) {
      setBookingErrorMsg('Please select a doctor and an available slot first.');
      return;
    }

    setIsBooking(true);
    setBookingErrorMsg('');
    setBookingSuccessMsg('');

    const bookingPayload = {
      providerId: selectedProvider.id,
      appointmentDate: selectedDate,
      startTime: selectedSlot.startTime,
      endTime: selectedSlot.endTime,
      notes: bookingNotes.trim(),
    };

    const result = await bookAppointment(bookingPayload);
    setIsBooking(false);

    if (result.success) {
      const tokenDisplay = result.data.tokenDisplay || (result.data.tokenNumber ? `T-${String(result.data.tokenNumber).padStart(3, '0')}` : 'T-001');
      setBookingSuccessMsg(`Appointment booked successfully! Your Queue Token: #${tokenDisplay} (Status: ${result.data.tokenStatus || 'WAITING'})`);
      setLastBookedToken(result.data);
      setBookingNotes('');
      setSelectedSlot(null);
      // Immediately refresh slots to reflect the newly booked slot as booked
      loadSlots(selectedProvider.id, selectedDate);
      loadCustomerAppointments();
      // For general customer view, switch to "My Appointments" so patient immediately sees their booking in list
      if (activeTab === 'providers') {
        setActiveTab('my-appointments');
      }
    } else {
      setBookingErrorMsg(result.error);
    }
  };

  // Handle Appointment Cancellation (Phase 4)
  const handleCancelAppointment = async (apptId) => {
    const reason = prompt('Please enter a cancellation reason (optional):', 'Schedule change');
    if (reason === null) return; // User pressed Cancel on prompt

    setCancellingId(apptId);
    setCancelErrorMsg('');
    setCancelSuccessMsg('');

    const result = await cancelAppointment(apptId, reason);
    setCancellingId(null);

    if (result.success) {
      setCancelSuccessMsg(`Appointment #${apptId} has been cancelled.`);
      loadCustomerAppointments();
      if (selectedProvider) {
        loadSlots(selectedProvider.id, selectedDate);
      }
    } else {
      setCancelErrorMsg(result.error);
    }
  };

  // Handle Provider Confirm Appointment
  const handleConfirmAppointmentAction = async (apptId) => {
    setAppointmentsError('');
    const result = await confirmAppointment(apptId);
    if (result.success) {
      loadCustomerAppointments();
    } else {
      setAppointmentsError(result.error);
    }
  };

  // Handle Provider Complete Appointment
  const handleCompleteAppointmentAction = async (apptId) => {
    setAppointmentsError('');
    const result = await completeAppointment(apptId);
    if (result.success) {
      loadCustomerAppointments();
    } else {
      setAppointmentsError(result.error);
    }
  };

  // View Status History Modal
  const handleViewHistory = async (apptId) => {
    setHistoryModalApptId(apptId);
    setIsLoadingHistory(true);
    const result = await fetchAppointmentHistory(apptId);
    setIsLoadingHistory(false);
    if (result.success) {
      setAppointmentHistory(result.data);
    } else {
      setAppointmentHistory([]);
    }
  };

  // Open Edit Appointment Form
  const handleOpenEditAppt = async (appt) => {
    setEditingAppt(appt);
    setEditNotes(appt.notes || '');
    setEditDate(appt.appointmentDate);
    setEditSlot(null);
    setEditError('');
    setEditSuccessMsg('');

    setIsLoadingEditSlots(true);
    const slotsRes = await fetchProviderSlots(appt.providerId, appt.appointmentDate);
    setIsLoadingEditSlots(false);
    if (slotsRes.success && slotsRes.data && Array.isArray(slotsRes.data.slots)) {
      setEditSlotsList(slotsRes.data.slots);
    } else {
      setEditSlotsList([]);
    }
  };

  // Change date in edit form and reload slots
  const handleEditDateChange = async (newDate) => {
    setEditDate(newDate);
    setEditSlot(null);
    if (!editingAppt) return;
    setIsLoadingEditSlots(true);
    const slotsRes = await fetchProviderSlots(editingAppt.providerId, newDate);
    setIsLoadingEditSlots(false);
    if (slotsRes.success && slotsRes.data && Array.isArray(slotsRes.data.slots)) {
      setEditSlotsList(slotsRes.data.slots);
    } else {
      setEditSlotsList([]);
    }
  };

  // Submit Edit / Reschedule
  const handleSaveEditAppt = async (e) => {
    e.preventDefault();
    if (!editingAppt) return;
    setIsSavingEdit(true);
    setEditError('');

    const payload = {
      notes: editNotes,
    };
    if (editSlot) {
      payload.appointmentDate = editDate;
      payload.startTime = editSlot.startTime;
      payload.endTime = editSlot.endTime;
    } else if (editDate !== editingAppt.appointmentDate) {
      setEditError('Please select a time slot for the new date.');
      setIsSavingEdit(false);
      return;
    }

    const result = await updateAppointmentDetails(editingAppt.id, payload);
    setIsSavingEdit(false);

    if (result.success) {
      setEditSuccessMsg(`Appointment #${editingAppt.id} successfully updated.`);
      setEditingAppt(null);
      loadCustomerAppointments();
    } else {
      setEditError(result.error);
    }
  };

  // Toggle day checkbox for provider schedule
  const handleDayToggle = (day) => {
    setProviderForm((prev) => {
      const exists = prev.workingDays.includes(day);
      let updated;
      if (exists) {
        updated = prev.workingDays.filter((d) => d !== day);
      } else {
        updated = [...prev.workingDays, day];
      }
      return { ...prev, workingDays: updated };
    });
  };

  // Save provider profile and schedule
  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setProfileMsg({ type: '', text: '' });

    if (!providerForm.displayName.trim()) {
      setProfileMsg({ type: 'error', text: 'Display name is required.' });
      return;
    }

    if (providerForm.workingDays.length === 0) {
      setProfileMsg({ type: 'error', text: 'Please select at least one working day.' });
      return;
    }

    if (providerForm.openTime >= providerForm.closeTime) {
      setProfileMsg({ type: 'error', text: 'Opening time must be earlier than closing time.' });
      return;
    }

    setIsSavingProfile(true);
    const result = await updateMyProviderProfile(providerForm);
    setIsSavingProfile(false);

    if (result.success) {
      setProfileMsg({ type: 'success', text: 'Provider profile and availability schedule updated successfully!' });
      loadProviders();
      if (selectedProvider && selectedProvider.id === result.data.id) {
        setSelectedProvider(result.data);
      }
    } else {
      setProfileMsg({ type: 'error', text: result.error });
    }
  };

  return (
    <div className="container">
      {/* Hero Header */}
      <header className="hero">
        <span className="hero-tag">CareQueue Healthcare Platform</span>
        <h1 className="hero-title" style={{ cursor: 'pointer' }} onClick={() => navigateTo('/')}>
          CareQueue
        </h1>
        <p className="hero-subtitle">
          Real-time patient appointments with queue position tracking, estimated wait times, provider consultation workflow, and administrator oversight.
        </p>
      </header>

      {/* Global Alerts */}
      {authSuccess && <div className="alert alert-success" style={{ marginBottom: '1.5rem' }}>{authSuccess}</div>}
      {authError && <div className="alert alert-error" style={{ marginBottom: '1.5rem' }}>{authError}</div>}

      {/* ROUTE 1: Separate Role Authentication Pages (/admin/auth, /doctor/auth, /patient/auth) */}
      {activeRoleConfig ? (
        <RoleAuthView
          config={activeRoleConfig}
          onBack={() => navigateTo('/')}
          authTab={authTab}
          setAuthTab={(t) => { setAuthTab(t); setAuthError(''); setAuthSuccess(''); }}
          loginEmail={loginEmail}
          setLoginEmail={setLoginEmail}
          loginPassword={loginPassword}
          setLoginPassword={setLoginPassword}
          regName={regName}
          setRegName={setRegName}
          regEmail={regEmail}
          setRegEmail={setRegEmail}
          regPassword={regPassword}
          setRegPassword={setRegPassword}
          isSubmitting={isSubmitting}
          authError={authError}
          authSuccess={authSuccess}
          onSubmitLogin={handleRoleLogin}
          onSubmitRegister={handleRoleRegister}
          currentUser={currentUser}
          onLogout={handleLogout}
          onGoToPortal={() => {
            if (currentUser.role === 'ADMIN') setActiveTab('admin-dashboard');
            else if (currentUser.role === 'PROVIDER') setActiveTab('clinic-queue');
            else setActiveTab('providers');
            navigateTo('/');
          }}
        />
      ) : !currentUser ? (
        /* ROUTE 2: Unauthenticated Landing Page on "/" */
        <LandingPage
          onSelectRole={(path) => navigateTo(path)}
          currentUser={null}
        />
      ) : (
        /* ROUTE 3: Authenticated User Portal on "/" */
        <div>
          {/* Main Navigation Bar */}
          <nav className="nav-tabs">
            <button
              className={`nav-tab-btn ${activeTab === 'landing' ? 'active' : ''}`}
              onClick={() => setActiveTab('landing')}
            >
              🏠 Home & Roles
            </button>

            <button
              className={`nav-tab-btn ${activeTab === 'providers' ? 'active' : ''}`}
              onClick={() => setActiveTab('providers')}
            >
              🏥 Doctors & Slots
            </button>

            <button
              className={`nav-tab-btn ${activeTab === 'my-appointments' ? 'active' : ''}`}
              onClick={() => setActiveTab('my-appointments')}
            >
              📅 My Appointments {myAppointments.length > 0 && <span className="tab-count">{myAppointments.length}</span>}
            </button>

            <button
              className={`nav-tab-btn ${activeTab === 'queue-tracker' ? 'active' : ''}`}
              onClick={() => setActiveTab('queue-tracker')}
            >
              ⏳ Live Queue Tracker {myActiveToken && myActiveToken.status === 'WAITING' && (
                <span className="tab-count pulse-badge">#{myActiveToken.tokenDisplay}</span>
              )}
            </button>

            {(currentUser.role === 'PROVIDER' || currentUser.role === 'ADMIN') && (
              <button
                className={`nav-tab-btn ${activeTab === 'clinic-queue' ? 'active' : ''}`}
                onClick={() => setActiveTab('clinic-queue')}
              >
                📋 Clinic Queue {providerQueue.length > 0 && <span className="tab-count">{providerQueue.length}</span>}
              </button>
            )}

            {(currentUser.role === 'PROVIDER' || currentUser.role === 'ADMIN') && (
              <button
                className={`nav-tab-btn ${activeTab === 'my-schedule' ? 'active' : ''}`}
                onClick={() => setActiveTab('my-schedule')}
              >
                ⚙️ My Provider Schedule
              </button>
            )}

            {currentUser.role === 'ADMIN' && (
              <button
                className={`nav-tab-btn ${activeTab === 'admin-dashboard' ? 'active' : ''}`}
                onClick={() => setActiveTab('admin-dashboard')}
              >
                📊 Admin Dashboard
              </button>
            )}

            <button
              className={`nav-tab-btn ${activeTab === 'auth' ? 'active' : ''}`}
              onClick={() => setActiveTab('auth')}
            >
              👤 {currentUser.name} ({currentUser.role})
            </button>
          </nav>

          {/* TAB 0: Landing Page when inside portal */}
          {activeTab === 'landing' && (
            <LandingPage
              onSelectRole={(path) => navigateTo(path)}
              currentUser={currentUser}
              onGoToPortal={() => {
                if (currentUser.role === 'ADMIN') setActiveTab('admin-dashboard');
                else if (currentUser.role === 'PROVIDER') setActiveTab('clinic-queue');
                else setActiveTab('providers');
              }}
              onLogout={handleLogout}
            />
          )}

      {/* Clinic Overview Meta Card (Active Doctors & Last Checked + Refresh) */}
      {activeTab !== 'landing' && (
        <section className="clinic-meta-card" aria-label="Clinic Overview">
          <div className="clinic-meta-item">
            <span className="clinic-meta-icon" aria-hidden="true">🩺</span>
            <div>
              <span className="clinic-meta-label">Active Doctors:</span>{' '}
              <strong className="clinic-meta-value">{providers.length} listed</strong>
            </div>
          </div>
          <div className="clinic-meta-divider" aria-hidden="true" />
          <div className="clinic-meta-item">
            <span className="clinic-meta-icon" aria-hidden="true">🕒</span>
            <div>
              <span className="clinic-meta-label">Last Checked:</span>{' '}
              <strong className="clinic-meta-value">{lastChecked || 'Just now'}</strong>
            </div>
          </div>
          <button
            className="btn btn-secondary clinic-meta-refresh-btn"
            onClick={loadProviders}
            disabled={isLoadingProviders}
            title="Refresh doctor listings and update last checked timestamp"
          >
            {isLoadingProviders ? 'Refreshing...' : '↻ Refresh'}
          </button>
        </section>
      )}

      {/* ========================================================================= */}
      {/* TAB 1: BROWSE DOCTORS & BOOK SLOTS (Customer / Public View) */}
      {/* ========================================================================= */}
      {activeTab === 'providers' && (
        <section className="section-card">
          <div className="section-header">
            <div>
              <h2 className="section-title">Select Doctor & Available Slot</h2>
              <p className="section-subtitle">
                Choose a healthcare specialist and pick an open consultation time window.
              </p>
            </div>
            <button
              className="btn btn-secondary"
              onClick={loadProviders}
              disabled={isLoadingProviders}
              style={{ fontSize: '0.85rem', padding: '0.4rem 0.8rem' }}
            >
              {isLoadingProviders ? 'Refreshing...' : '↻ Refresh List'}
            </button>
          </div>

          <BookingFlow
            currentUser={currentUser}
            providers={providers}
            bookableProviders={bookableProviders}
            selectedProvider={selectedProvider}
            setSelectedProvider={setSelectedProvider}
            selectedDate={selectedDate}
            setSelectedDate={setSelectedDate}
            slotData={slotData}
            isLoadingSlots={isLoadingSlots}
            slotError={slotError}
            selectedSlot={selectedSlot}
            setSelectedSlot={setSelectedSlot}
            bookingNotes={bookingNotes}
            setBookingNotes={setBookingNotes}
            isBooking={isBooking}
            bookingSuccessMsg={bookingSuccessMsg}
            bookingErrorMsg={bookingErrorMsg}
            providerListError={providerListError}
            isLoadingProviders={isLoadingProviders}
            loadProviders={loadProviders}
            handleConfirmBooking={handleConfirmBooking}
            onGoToLogin={() => setActiveTab('auth')}
            onReloadSlots={() => selectedProvider && loadSlots(selectedProvider.id, selectedDate)}
            lastBookedToken={lastBookedToken}
            setLastBookedToken={setLastBookedToken}
            onViewMyAppointments={() => setActiveTab('my-appointments')}
            bannerNotice={
              currentUser && currentUser.role === 'PROVIDER'
                ? '🩺 You are signed in as a Doctor. You can book an appointment for yourself with another doctor below (self-booking is prohibited).'
                : currentUser && currentUser.role === 'ADMIN'
                ? '🛡️ You are signed in as an Admin. You can book an appointment for yourself with any doctor below.'
                : null
            }
          />
        </section>
      )}

      {/* ========================================================================= */}
      {/* TAB 2: MY APPOINTMENTS & STATUS (Customer View) */}
      {/* ========================================================================= */}
      {activeTab === 'my-appointments' && (
        <section className="section-card">
          <div className="section-header">
            <div>
              <h2 className="section-title">My Booked Appointments</h2>
              <p className="section-subtitle">
                Track your upcoming consultations, view status history, and cancel eligible visits.
              </p>
            </div>
            <button
              className="btn btn-secondary"
              onClick={loadCustomerAppointments}
              disabled={isLoadingAppointments}
              style={{ fontSize: '0.85rem', padding: '0.4rem 0.8rem' }}
            >
              {isLoadingAppointments ? 'Refreshing...' : '↻ Refresh Appointments'}
            </button>
          </div>

          {cancelSuccessMsg && <div className="alert alert-success">{cancelSuccessMsg}</div>}
          {cancelErrorMsg && <div className="alert alert-error">{cancelErrorMsg}</div>}
          {editSuccessMsg && <div className="alert alert-success">{editSuccessMsg}</div>}
          {appointmentsError && <div className="alert alert-error">{appointmentsError}</div>}

          {/* Newly Booked Token Highlight Banner */}
          {lastBookedToken && (
            <div className="token-booked-banner">
              <div className="token-banner-icon">🎟️</div>
              <div className="token-banner-body">
                <h3>Your Clinic Queue Token: #{lastBookedToken.tokenDisplay || ('T-00' + lastBookedToken.tokenNumber)}</h3>
                <p>
                  Appointment confirmed for <strong>{lastBookedToken.appointmentDate}</strong> at <strong>{lastBookedToken.startTime}</strong> with <strong>{lastBookedToken.providerName}</strong>.
                  Your queue status is <span className="status-badge status-badge-waiting">{lastBookedToken.tokenStatus || 'WAITING'}</span>.
                </p>
              </div>
              <button className="btn-close-banner" onClick={() => setLastBookedToken(null)} title="Dismiss">✕</button>
            </div>
          )}

          {!currentUser ? (
            <div className="empty-state">
              <div className="empty-icon">🔒</div>
              <h3>Please Log In</h3>
              <p>Sign in to your patient account to view and manage your booked appointments.</p>
              <button
                className="btn btn-primary"
                style={{ marginTop: '0.75rem' }}
                onClick={() => setActiveTab('auth')}
              >
                Sign In
              </button>
            </div>
          ) : isLoadingAppointments ? (
            <div className="loading-state">
              <div className="spinner"></div>
              <p>Loading your appointments...</p>
            </div>
          ) : myAppointments.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">📅</div>
              <h3>No Appointments Booked Yet</h3>
              <p>You haven't scheduled any appointments. Browse available doctors to book your visit.</p>
              <button
                className="btn btn-primary"
                style={{ marginTop: '0.75rem' }}
                onClick={() => setActiveTab('providers')}
              >
                Find a Doctor
              </button>
            </div>
          ) : (
            <div className="appointments-list">
              {myAppointments.map((appt) => {
                const isCancelling = cancellingId === appt.id;
                const statusStr = appt.status || 'PENDING';
                const canCancel = statusStr === 'PENDING' || statusStr === 'CONFIRMED';

                return (
                  <div key={appt.id} className={`appointment-card status-${statusStr.toLowerCase()}`}>
                    <div className="appointment-card-top">
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem', flexWrap: 'wrap' }}>
                          <h3 className="appointment-doctor">{appt.providerName || 'Healthcare Provider'}</h3>
                          <span className={`status-badge status-badge-${statusStr.toLowerCase()}`}>
                            {statusStr}
                          </span>
                          {appt.tokenDisplay && (
                            <span className={`token-pill status-${(appt.tokenStatus || 'waiting').toLowerCase()}`}>
                              🎟️ Token #{appt.tokenDisplay} ({appt.tokenStatus || 'WAITING'})
                            </span>
                          )}
                        </div>
                        <span className="appointment-specialty">{appt.serviceDescription || 'General Consultation'}</span>
                      </div>

                      <span className="appointment-id-badge">ID #{appt.id}</span>
                    </div>

                    <div className="appointment-details-grid">
                      <div className="detail-item">
                        <span className="detail-item-label">Date</span>
                        <span className="detail-item-val">{appt.appointmentDate}</span>
                      </div>
                      <div className="detail-item">
                        <span className="detail-item-label">Time Window</span>
                        <span className="detail-item-val">{appt.startTime} – {appt.endTime}</span>
                      </div>
                      <div className="detail-item">
                        <span className="detail-item-label">Patient</span>
                        <span className="detail-item-val">{appt.customerName}</span>
                      </div>
                      <div className="detail-item">
                        <span className="detail-item-label">Notes</span>
                        <span className="detail-item-val">{appt.notes || 'None specified'}</span>
                      </div>
                    </div>

                    <div className="appointment-card-actions">
                      {currentUser && (currentUser.role === 'PROVIDER' || currentUser.role === 'ADMIN') && statusStr === 'PENDING' && (
                        <button
                          className="btn btn-success"
                          style={{ fontSize: '0.825rem', padding: '0.4rem 0.85rem', backgroundColor: '#059669', color: '#fff', border: 'none' }}
                          onClick={() => handleConfirmAppointmentAction(appt.id)}
                        >
                          ✓ Confirm Appointment
                        </button>
                      )}

                      {currentUser && (currentUser.role === 'PROVIDER' || currentUser.role === 'ADMIN') && (statusStr === 'PENDING' || statusStr === 'CONFIRMED') && (
                        <button
                          className="btn btn-success"
                          style={{ fontSize: '0.825rem', padding: '0.4rem 0.85rem', backgroundColor: '#059669', color: '#fff', border: 'none' }}
                          onClick={() => handleCompleteAppointmentAction(appt.id)}
                        >
                          ✅ Mark Completed
                        </button>
                      )}

                      {canCancel && (
                        <button
                          className="btn btn-secondary"
                          style={{ fontSize: '0.825rem', padding: '0.4rem 0.85rem' }}
                          onClick={() => handleOpenEditAppt(appt)}
                        >
                          ✏️ Edit / Reschedule
                        </button>
                      )}

                      {canCancel && (
                        <button
                          className="btn btn-danger"
                          style={{ fontSize: '0.825rem', padding: '0.4rem 0.85rem' }}
                          onClick={() => handleCancelAppointment(appt.id)}
                          disabled={isCancelling}
                        >
                          {isCancelling ? 'Cancelling...' : '✕ Cancel Appointment'}
                        </button>
                      )}

                      <button
                        className="btn btn-secondary"
                        style={{ fontSize: '0.825rem', padding: '0.4rem 0.85rem' }}
                        onClick={() => handleViewHistory(appt.id)}
                      >
                        📜 View Audit History
                      </button>
                    </div>

                    {/* Expandable Edit / Reschedule Drawer */}
                    {editingAppt && editingAppt.id === appt.id && (
                      <div className="history-drawer" style={{ background: '#f8fafc', border: '1.5px solid #cbd5e1', marginBottom: '0.75rem' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                          <h4 style={{ margin: 0, fontSize: '0.95rem', color: '#0f172a' }}>
                            ✏️ Update Appointment Details (ID #{appt.id})
                          </h4>
                          <button className="btn-text" onClick={() => setEditingAppt(null)}>Close ✕</button>
                        </div>

                        {editError && <div className="alert alert-error" style={{ marginBottom: '0.75rem' }}>{editError}</div>}

                        <form onSubmit={handleSaveEditAppt}>
                          <div style={{ marginBottom: '0.75rem' }}>
                            <label className="form-label" style={{ fontSize: '0.85rem' }}>Reason for Visit / Clinical Notes:</label>
                            <textarea
                              className="form-input"
                              rows="2"
                              value={editNotes}
                              onChange={(e) => setEditNotes(e.target.value)}
                              placeholder="Update symptoms or reason for visit..."
                            />
                          </div>

                          <div style={{ marginBottom: '0.75rem' }}>
                            <label className="form-label" style={{ fontSize: '0.85rem' }}>Reschedule Date:</label>
                            <input
                              type="date"
                              className="form-input"
                              value={editDate}
                              min={getTodayString()}
                              onChange={(e) => handleEditDateChange(e.target.value)}
                            />
                          </div>

                          <div style={{ marginBottom: '1rem' }}>
                            <label className="form-label" style={{ fontSize: '0.85rem' }}>
                              Available Slots for {editDate}: {editSlot ? <strong>Selected: {editSlot.startTime} – {editSlot.endTime}</strong> : '(Keep current slot or click to select new slot)'}
                            </label>
                            {isLoadingEditSlots ? (
                              <p style={{ fontSize: '0.85rem', color: '#64748b' }}>Loading available slots...</p>
                            ) : editSlotsList.length === 0 ? (
                              <p style={{ fontSize: '0.85rem', color: '#ef4444' }}>No consultation slots available on {editDate}.</p>
                            ) : (
                              <div className="slots-grid" style={{ maxHeight: '180px', overflowY: 'auto' }}>
                                {editSlotsList.map((slot) => {
                                  const isSelected = editSlot && editSlot.startTime === slot.startTime;
                                  const isUnavailable = !slot.available || slot.booked || slot.past || slot.isPast;
                                  return (
                                    <button
                                      key={slot.startTime}
                                      type="button"
                                      className={`slot-pill ${isSelected ? 'selected' : ''} ${isUnavailable ? 'unavailable' : ''}`}
                                      disabled={isUnavailable}
                                      onClick={() => setEditSlot(slot)}
                                      style={{ fontSize: '0.8rem', padding: '0.35rem 0.6rem' }}
                                    >
                                      {slot.startTime} – {slot.endTime}
                                    </button>
                                  );
                                })}
                              </div>
                            )}
                          </div>

                          <div style={{ display: 'flex', gap: '0.5rem' }}>
                            <button
                              type="submit"
                              className="btn btn-primary"
                              disabled={isSavingEdit}
                              style={{ fontSize: '0.85rem', padding: '0.45rem 1rem' }}
                            >
                              {isSavingEdit ? 'Saving...' : '💾 Save Changes'}
                            </button>
                            <button
                              type="button"
                              className="btn btn-secondary"
                              onClick={() => setEditingAppt(null)}
                              style={{ fontSize: '0.85rem', padding: '0.45rem 0.85rem' }}
                            >
                              Cancel
                            </button>
                          </div>
                        </form>
                      </div>
                    )}

                    {/* Expandable History Drawer */}
                    {historyModalApptId === appt.id && (
                      <div className="history-drawer">
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                          <h4 style={{ margin: 0, fontSize: '0.9rem', color: '#1e293b' }}>
                            Status Transition Audit Trail:
                          </h4>
                          <button
                            className="btn-text"
                            onClick={() => setHistoryModalApptId(null)}
                          >
                            Close ✕
                          </button>
                        </div>

                        {isLoadingHistory ? (
                          <p style={{ fontSize: '0.85rem', color: '#64748b' }}>Loading history...</p>
                        ) : appointmentHistory.length === 0 ? (
                          <p style={{ fontSize: '0.85rem', color: '#64748b' }}>No status transitions recorded.</p>
                        ) : (
                          <ul className="history-timeline">
                            {appointmentHistory.map((h) => (
                              <li key={h.id} className="history-entry">
                                <span className="history-bullet">•</span>
                                <div>
                                  <div className="history-status-change">
                                    Transition: <strong>{h.fromStatus || 'INIT'}</strong> ➔ <strong>{h.toStatus}</strong>
                                  </div>
                                  <div className="history-reason">
                                    {h.reasonOrNotes || 'No notes specified'}
                                  </div>
                                  <div className="history-time">
                                    {new Date(h.createdAt).toLocaleString()} • Changed by: <strong>{h.changedByUserName || ('User #' + h.changedByUserId)}</strong> ({h.changedByUserRole || 'USER'})
                                  </div>
                                </div>
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </section>
      )}

      {/* ========================================================================= */}
      {/* TAB: CUSTOMER LIVE QUEUE TRACKER (Phase 6) */}
      {/* ========================================================================= */}
      {activeTab === 'queue-tracker' && (
        <section className="section-card">
          <div className="section-header">
            <div>
              <h2 className="section-title">Live Clinic Queue Tracker</h2>
              <p className="section-subtitle">
                Track your active consultation queue position, people ahead of you, and estimated waiting time in real time.
              </p>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
              <div className="poll-indicator">
                {myActiveToken && isTerminalQueueStatus(myActiveToken.status) ? (
                  <>
                    <span className="poll-dot" style={{ backgroundColor: '#94a3b8', animation: 'none' }}></span>
                    <span>Tracking concluded ({myActiveToken.status}) • Polling stopped</span>
                  </>
                ) : (
                  <>
                    <span className="poll-dot"></span>
                    <span>Auto-refreshes every 10s • Last: {lastMyTokenUpdated || 'Just now'}</span>
                  </>
                )}
              </div>
              <button
                className="btn btn-secondary"
                onClick={() => loadMyQueueToken(false)}
                disabled={isLoadingMyToken}
                style={{ fontSize: '0.85rem', padding: '0.45rem 0.85rem' }}
              >
                {isLoadingMyToken ? 'Refreshing...' : '↻ Refresh Position'}
              </button>
            </div>
          </div>

          {myTokenError && <div className="alert alert-error">{myTokenError}</div>}

          {!currentUser ? (
            <div className="empty-state">
              <div className="empty-icon">🔒</div>
              <h3>Please Log In</h3>
              <p>Sign in to your patient account to track your clinic queue position.</p>
              <button
                className="btn btn-primary"
                style={{ marginTop: '0.75rem' }}
                onClick={() => setActiveTab('auth')}
              >
                Sign In
              </button>
            </div>
          ) : isLoadingMyToken && !myActiveToken ? (
            <div className="loading-state">
              <div className="spinner"></div>
              <p>Locating your active queue position...</p>
            </div>
          ) : !myActiveToken ? (
            <div className="empty-state">
              <div className="empty-icon">🎟️</div>
              <h3>No Active Queue Position</h3>
              <p>
                You don't have an active consultation queue token right now.
                Schedule an appointment to receive your queue position.
              </p>
              <button
                className="btn btn-primary"
                style={{ marginTop: '0.75rem' }}
                onClick={() => setActiveTab('providers')}
              >
                Book an Appointment
              </button>
            </div>
          ) : (
            <div>
              {/* Approaching / Called In-App Notification Toast */}
              {queueNotification && (
                <div className={`queue-toast-banner toast-${queueNotification.type}`}>
                  <div>
                    <strong>{queueNotification.title}</strong> {queueNotification.message}
                  </div>
                  <button
                    className="btn-close-toast"
                    onClick={() => setQueueNotification(null)}
                    title="Dismiss alert"
                    aria-label="Dismiss alert"
                  >
                    ✕
                  </button>
                </div>
              )}

              {/* Primary Active Token Hero Card */}
              <div className={`live-tracker-hero status-${myActiveToken.status.toLowerCase()}`}>
                <div className="tracker-hero-top">
                  <div>
                    <span className="tracker-tag">CURRENT APPOINTMENT</span>
                    <h3 className="tracker-doctor">{myActiveToken.providerName}</h3>
                    <p className="tracker-specialty">{myActiveToken.serviceDescription}</p>
                    <p className="tracker-datetime">
                      📅 <strong>{myActiveToken.appointmentDate}</strong> • ⏰ <strong>{myActiveToken.startTime} – {myActiveToken.endTime}</strong>
                    </p>
                  </div>

                  <div className="tracker-token-display">
                    <span className="token-label">YOUR TOKEN</span>
                    <div className="token-number-hero">#{myActiveToken.tokenDisplay}</div>
                    <span className={`status-badge status-badge-${myActiveToken.status.toLowerCase()}`}>
                      {myActiveToken.status}
                    </span>
                  </div>
                </div>

                {/* Queue Position Metrics Grid */}
                <div className="tracker-metrics-grid">
                  <div className="tracker-metric-card">
                    <span className="metric-icon">👥</span>
                    <div className="metric-info">
                      <span className="metric-val">
                        {myActiveToken.status === 'CALLED' ? '0 (Your Turn!)' :
                         myActiveToken.status === 'IN_PROGRESS' ? '0 (In Room)' :
                         ['COMPLETED', 'CANCELLED', 'SKIPPED'].includes(myActiveToken.status) ? 'Concluded' :
                         `#${myActiveToken.queuePosition || (myActiveToken.peopleAhead + 1)} (${myActiveToken.peopleAhead} ahead)`}
                      </span>
                      <span className="metric-label">Queue Position</span>
                    </div>
                  </div>

                  <div className="tracker-metric-card">
                    <span className="metric-icon">⏱️</span>
                    <div className="metric-info">
                      <span className="metric-val">
                        {myActiveToken.status === 'CALLED' ? 'Now Calling!' :
                         myActiveToken.status === 'IN_PROGRESS' ? 'Consulting Now' :
                         ['COMPLETED', 'CANCELLED', 'SKIPPED'].includes(myActiveToken.status) ? '0 mins' :
                         `~${myActiveToken.estimatedWaitMinutes} mins`}
                      </span>
                      <span className="metric-label">Estimated Wait Time</span>
                    </div>
                  </div>

                  <div className="tracker-metric-card">
                    <span className="metric-icon">⏳</span>
                    <div className="metric-info">
                      <span className="metric-val">{myActiveToken.slotDurationMinutes} mins</span>
                      <span className="metric-label">Consultation Window</span>
                    </div>
                  </div>

                  <div className="tracker-metric-card">
                    <span className="metric-icon">📢</span>
                    <div className="metric-info">
                      <span className="metric-val">{myActiveToken.currentlyServingToken || 'None'}</span>
                      <span className="metric-label">Now Serving</span>
                    </div>
                  </div>
                </div>

                {/* Status-specific Announcement Banner */}
                {myActiveToken.status === 'WAITING' && myActiveToken.peopleAhead <= QUEUE_APPROACHING_THRESHOLD && (
                  <div className="tracker-call-alert" style={{ background: '#fef3c7', borderColor: '#f59e0b', color: '#92400e' }}>
                    🔔 <strong>YOUR TURN IS APPROACHING!</strong> You are #{myActiveToken.queuePosition || (myActiveToken.peopleAhead + 1)} in line ({myActiveToken.peopleAhead} patient{myActiveToken.peopleAhead === 1 ? '' : 's'} ahead). Please get ready near {myActiveToken.providerName}'s consultation room.
                  </div>
                )}
                {myActiveToken.status === 'CALLED' && (
                  <div className="tracker-call-alert">
                    🚨 <strong>YOUR TOKEN IS BEING CALLED!</strong> Please proceed to {myActiveToken.providerName}'s consultation room now.
                  </div>
                )}

                {myActiveToken.status === 'IN_PROGRESS' && (
                  <div className="tracker-call-alert" style={{ background: '#f3e8ff', borderColor: '#c084fc', color: '#6b21a8' }}>
                    🩺 <strong>CONSULTATION IN PROGRESS:</strong> You are currently in consultation with {myActiveToken.providerName}.
                  </div>
                )}

                {myActiveToken.status === 'WAITING' && myActiveToken.peopleAhead > QUEUE_APPROACHING_THRESHOLD && (
                  <div className="tracker-call-alert" style={{ background: '#eff6ff', borderColor: '#93c5fd', color: '#1e40af' }}>
                    ℹ️ <strong>WAITING IN QUEUE:</strong> Please remain near the clinic waiting area. You are #{myActiveToken.queuePosition || (myActiveToken.peopleAhead + 1)} in line ({myActiveToken.peopleAhead} ahead). Estimated wait is calculated from {myActiveToken.peopleAhead} patient(s) ahead × {myActiveToken.slotDurationMinutes} min duration.
                  </div>
                )}

                {myActiveToken.status === 'COMPLETED' && (
                  <div className="tracker-call-alert" style={{ background: '#f0fdf4', borderColor: '#86efac', color: '#166534' }}>
                    ✅ <strong>CONSULTATION COMPLETED:</strong> Your appointment with {myActiveToken.providerName} has concluded. Tracking is complete.
                  </div>
                )}

                {myActiveToken.status === 'SKIPPED' && (
                  <div className="tracker-call-alert" style={{ background: '#fffbeb', borderColor: '#fcd34d', color: '#92400e' }}>
                    ⚠️ <strong>TOKEN SKIPPED:</strong> Your token was passed by clinic staff. Please speak with the reception desk or doctor if you have arrived.
                  </div>
                )}

                {myActiveToken.status === 'CANCELLED' && (
                  <div className="tracker-call-alert" style={{ background: '#fef2f2', borderColor: '#fca5a5', color: '#991b1b' }}>
                    ❌ <strong>TOKEN CANCELLED:</strong> This consultation token or appointment was cancelled. Tracking has concluded.
                  </div>
                )}

                {/* Stepper Visualizer */}
                <div className="queue-stepper">
                  <div className="stepper-step completed">
                    <div className="step-circle">✓</div>
                    <span className="step-title">Booked</span>
                  </div>
                  <div className="stepper-line completed"></div>

                  <div className={`stepper-step ${!['CANCELLED', 'SKIPPED'].includes(myActiveToken.status) ? 'completed' : 'cancelled'}`}>
                    <div className="step-circle">{['CANCELLED', 'SKIPPED'].includes(myActiveToken.status) ? '✕' : '2'}</div>
                    <span className="step-title">{myActiveToken.status === 'SKIPPED' ? 'Skipped' : myActiveToken.status === 'CANCELLED' ? 'Cancelled' : 'In Queue'}</span>
                  </div>
                  <div className={`stepper-line ${['CALLED', 'IN_PROGRESS', 'COMPLETED'].includes(myActiveToken.status) ? 'completed' : ''}`}></div>

                  <div className={`stepper-step ${['CALLED', 'IN_PROGRESS', 'COMPLETED'].includes(myActiveToken.status) ? 'completed' : ''}`}>
                    <div className="step-circle">3</div>
                    <span className="step-title">Called</span>
                  </div>
                  <div className={`stepper-line ${['IN_PROGRESS', 'COMPLETED'].includes(myActiveToken.status) ? 'completed' : ''}`}></div>

                  <div className={`stepper-step ${['IN_PROGRESS', 'COMPLETED'].includes(myActiveToken.status) ? 'completed' : ''}`}>
                    <div className="step-circle">4</div>
                    <span className="step-title">Consulting</span>
                  </div>
                  <div className={`stepper-line ${myActiveToken.status === 'COMPLETED' ? 'completed' : ''}`}></div>

                  <div className={`stepper-step ${myActiveToken.status === 'COMPLETED' ? 'completed' : ''}`}>
                    <div className="step-circle">{myActiveToken.status === 'COMPLETED' ? '✓' : '5'}</div>
                    <span className="step-title">Completed</span>
                  </div>
                </div>
              </div>

              {/* Patient's Other Queue Tokens List (if any) */}
              {myQueueTokens.length > 1 && (
                <div style={{ marginTop: '2rem' }}>
                  <h4 style={{ color: '#1e293b', marginBottom: '0.75rem' }}>All My Queue Tokens ({myQueueTokens.length})</h4>
                  <div className="queue-tokens-mini-grid">
                    {myQueueTokens.map((qt) => (
                      <div
                        key={qt.tokenId}
                        className={`mini-token-card ${myActiveToken.tokenId === qt.tokenId ? 'active-selection' : ''}`}
                        onClick={() => setMyActiveToken(qt)}
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <span className="mini-token-badge">#{qt.tokenDisplay}</span>
                          <span className={`status-badge status-badge-${qt.status.toLowerCase()}`}>{qt.status}</span>
                        </div>
                        <div style={{ fontSize: '0.85rem', color: '#1e293b', marginTop: '0.4rem', fontWeight: 600 }}>
                          {qt.providerName}
                        </div>
                        <div style={{ fontSize: '0.775rem', color: '#64748b' }}>
                          {qt.appointmentDate} • {qt.startTime}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </section>
      )}

      {/* ========================================================================= */}
      {/* TAB: PROVIDER CLINIC QUEUE MANAGEMENT (Phase 5) */}
      {/* ========================================================================= */}
      {activeTab === 'clinic-queue' && currentUser && (currentUser.role === 'PROVIDER' || currentUser.role === 'ADMIN') && (
        <section className="section-card">
          <div className="section-header">
            <div>
              <h2 className="section-title">Daily Clinic Queue & Consultation Controls</h2>
              <p className="section-subtitle">
                Sequence and call waiting patients in order, advance consultation status, and manage daily clinic flow.
              </p>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
              <div className="date-picker-group">
                <label htmlFor="queueDate" className="date-picker-label">Queue Date:</label>
                <input
                  id="queueDate"
                  type="date"
                  className="form-input date-input"
                  value={queueDate}
                  onChange={(e) => setQueueDate(e.target.value)}
                />
              </div>

              <button
                className="btn btn-secondary"
                onClick={() => loadQueue(queueDate)}
                disabled={isLoadingQueue}
                style={{ fontSize: '0.85rem', padding: '0.45rem 0.85rem' }}
              >
                {isLoadingQueue ? 'Refreshing...' : '↻ Refresh Queue'}
              </button>
            </div>
          </div>

          {/* Doctor Dashboard Subtabs */}
          <div className="admin-subtabs" style={{ marginBottom: '1.25rem' }}>
            <button
              className={`admin-subtab-btn ${doctorQueueTab === 'queue' ? 'active' : ''}`}
              onClick={() => setDoctorQueueTab('queue')}
            >
              📋 Patient Queue {providerQueue.length > 0 && `(${providerQueue.length})`}
            </button>
            <button
              className={`admin-subtab-btn ${doctorQueueTab === 'book' ? 'active' : ''}`}
              onClick={() => setDoctorQueueTab('book')}
            >
              ➕ Book Appointment With Another Doctor
            </button>
          </div>

          {doctorQueueTab === 'book' ? (
            <BookingFlow
              currentUser={currentUser}
              providers={providers}
              bookableProviders={bookableProviders}
              selectedProvider={selectedProvider}
              setSelectedProvider={setSelectedProvider}
              selectedDate={selectedDate}
              setSelectedDate={setSelectedDate}
              slotData={slotData}
              isLoadingSlots={isLoadingSlots}
              slotError={slotError}
              selectedSlot={selectedSlot}
              setSelectedSlot={setSelectedSlot}
              bookingNotes={bookingNotes}
              setBookingNotes={setBookingNotes}
              isBooking={isBooking}
              bookingSuccessMsg={bookingSuccessMsg}
              bookingErrorMsg={bookingErrorMsg}
              providerListError={providerListError}
              isLoadingProviders={isLoadingProviders}
              loadProviders={loadProviders}
              handleConfirmBooking={handleConfirmBooking}
              onGoToLogin={() => setActiveTab('auth')}
              onReloadSlots={() => selectedProvider && loadSlots(selectedProvider.id, selectedDate)}
              lastBookedToken={lastBookedToken}
              setLastBookedToken={setLastBookedToken}
              onViewMyAppointments={() => setActiveTab('my-appointments')}
              bannerNotice="🩺 Doctor Peer Booking: Book an appointment for yourself with another specialist doctor in the clinic. Your own doctor profile is excluded from the list below."
            />
          ) : (
            <div>
              {queueSuccessMsg && <div className="alert alert-success">{queueSuccessMsg}</div>}
              {queueError && <div className="alert alert-error">{queueError}</div>}

          {/* Quick Date Options for Provider Queue */}
          <div className="date-options-wrapper" style={{ marginBottom: '1.25rem' }}>
            <span className="date-options-label">Select Date:</span>
            <div className="date-options-scroll">
              {getUpcomingDates(7).map((d) => {
                const isSelected = queueDate === d.dateString;
                return (
                  <button
                    key={d.dateString}
                    type="button"
                    className={`date-option-pill ${isSelected ? 'active' : ''}`}
                    onClick={() => setQueueDate(d.dateString)}
                  >
                    <span className="date-option-day">{d.label}</span>
                    <span className="date-option-status">{d.fullDayOfWeek.slice(0, 3)}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Call Next Patient Master Banner */}
          <div className="call-next-panel">
            <div className="call-next-info">
              <span className="call-next-tag">NEXT IN LINE</span>
              <h3 className="call-next-title">
                {(() => {
                  const nextToken = providerQueue.find((t) => t.status === 'WAITING');
                  if (nextToken) {
                    return `Ready to Call: #${nextToken.tokenDisplay} (${nextToken.customerName} at ${nextToken.startTime})`;
                  }
                  const activeToken = providerQueue.find((t) => t.status === 'CALLED' || t.status === 'IN_PROGRESS');
                  if (activeToken) {
                    return `Currently ${activeToken.status}: #${activeToken.tokenDisplay} (${activeToken.customerName})`;
                  }
                  return 'No patients waiting in queue right now';
                })()}
              </h3>
              <p className="call-next-desc">
                Patients are called in order of their sequential token number. Calling transitions the token from WAITING to CALLED.
              </p>
            </div>

            <button
              className="btn btn-call-next"
              onClick={handleCallNext}
              disabled={isCallingNext || !providerQueue.some((t) => t.status === 'WAITING')}
            >
              {isCallingNext ? (
                <span>Calling...</span>
              ) : (
                <span>📢 Call Next Waiting Patient ({providerQueue.filter((t) => t.status === 'WAITING').length} waiting)</span>
              )}
            </button>
          </div>

          {/* Queue Statistics Badges */}
          <div className="queue-stats-row">
            <div className="queue-stat-pill">
              <span className="queue-stat-num">{providerQueue.length}</span>
              <span className="queue-stat-label">Total Today</span>
            </div>
            <div className="queue-stat-pill pill-waiting">
              <span className="queue-stat-num">{providerQueue.filter((t) => t.status === 'WAITING').length}</span>
              <span className="queue-stat-label">Waiting</span>
            </div>
            <div className="queue-stat-pill pill-called">
              <span className="queue-stat-num">{providerQueue.filter((t) => t.status === 'CALLED').length}</span>
              <span className="queue-stat-label">Called</span>
            </div>
            <div className="queue-stat-pill pill-inprogress">
              <span className="queue-stat-num">{providerQueue.filter((t) => t.status === 'IN_PROGRESS').length}</span>
              <span className="queue-stat-label">In Consultation</span>
            </div>
            <div className="queue-stat-pill pill-completed">
              <span className="queue-stat-num">{providerQueue.filter((t) => t.status === 'COMPLETED').length}</span>
              <span className="queue-stat-label">Completed</span>
            </div>
            <div className="queue-stat-pill pill-skipped">
              <span className="queue-stat-num">{providerQueue.filter((t) => t.status === 'SKIPPED').length}</span>
              <span className="queue-stat-label">Skipped</span>
            </div>
            <div className="queue-stat-pill pill-cancelled">
              <span className="queue-stat-num">{providerQueue.filter((t) => t.status === 'CANCELLED').length}</span>
              <span className="queue-stat-label">Cancelled</span>
            </div>
          </div>

          {/* Queue Token List */}
          {isLoadingQueue ? (
            <div className="loading-state">
              <div className="spinner"></div>
              <p>Loading clinic queue for {queueDate}...</p>
            </div>
          ) : providerQueue.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">📋</div>
              <h3>Queue is Empty for {queueDate}</h3>
              <p>No patients have booked appointments with you on this date.</p>
            </div>
          ) : (
            <div className="queue-tokens-list">
              {providerQueue.map((token) => {
                const status = token.status || 'WAITING';
                const isUpdating = updatingTokenId === token.id;

                return (
                  <div key={token.id} className={`queue-token-card queue-card-${status.toLowerCase()}`}>
                    <div className="queue-token-left">
                      <div className={`token-badge-large token-badge-${status.toLowerCase()}`}>
                        #{token.tokenDisplay}
                      </div>

                      <div className="queue-token-info">
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
                          <h3 className="queue-patient-name">{token.customerName || 'Patient'}</h3>
                          <span className={`status-badge status-badge-${status.toLowerCase()}`}>
                            {status}
                          </span>
                        </div>
                        <div className="queue-patient-contact">
                          <span>📧 {token.customerEmail || 'No email'}</span>
                          <span>⏰ Scheduled: {token.startTime} – {token.endTime}</span>
                          {token.notes && <span>📝 {token.notes}</span>}
                        </div>
                        {(token.calledAt || token.completedAt) && (
                          <div className="queue-timestamps">
                            {token.calledAt && <span>Called: {new Date(token.calledAt).toLocaleTimeString()}</span>}
                            {token.completedAt && <span>Completed: {new Date(token.completedAt).toLocaleTimeString()}</span>}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Status Action Controls */}
                    <div className="queue-token-actions">
                      {status === 'WAITING' && (
                        <button
                          className="btn btn-primary"
                          style={{ fontSize: '0.825rem', padding: '0.4rem 0.8rem' }}
                          onClick={() => handleUpdateTokenStatus(token.id, 'CALLED')}
                          disabled={isUpdating}
                        >
                          📢 Call Patient
                        </button>
                      )}

                      {status === 'CALLED' && (
                        <>
                          <button
                            className="btn btn-success"
                            style={{ fontSize: '0.825rem', padding: '0.4rem 0.8rem', backgroundColor: '#059669', color: '#fff', border: 'none' }}
                            onClick={() => handleUpdateTokenStatus(token.id, 'IN_PROGRESS', 'Consultation in progress')}
                            disabled={isUpdating}
                          >
                            ▶️ Start Consultation
                          </button>
                          <button
                            className="btn btn-secondary"
                            style={{ fontSize: '0.825rem', padding: '0.4rem 0.8rem' }}
                            onClick={() => handleUpdateTokenStatus(token.id, 'SKIPPED', 'Patient not present when called')}
                            disabled={isUpdating}
                          >
                            ⏭️ Skip
                          </button>
                        </>
                      )}

                      {status === 'IN_PROGRESS' && (
                        <button
                          className="btn btn-success"
                          style={{ fontSize: '0.825rem', padding: '0.4rem 0.8rem', backgroundColor: '#059669', color: '#fff', border: 'none' }}
                          onClick={() => handleUpdateTokenStatus(token.id, 'COMPLETED', 'Consultation finished')}
                          disabled={isUpdating}
                        >
                          ✅ Complete Visit
                        </button>
                      )}

                      {status === 'SKIPPED' && (
                        <button
                          className="btn btn-secondary"
                          style={{ fontSize: '0.825rem', padding: '0.4rem 0.8rem' }}
                          onClick={() => handleUpdateTokenStatus(token.id, 'WAITING', 'Re-queued after skip')}
                          disabled={isUpdating}
                        >
                          ↩️ Re-queue
                        </button>
                      )}

                      {status !== 'COMPLETED' && status !== 'CANCELLED' && (
                        <button
                          className="btn btn-danger"
                          style={{ fontSize: '0.825rem', padding: '0.4rem 0.8rem' }}
                          onClick={() => {
                            if (window.confirm(`Are you sure you want to cancel Token #${token.tokenDisplay}?`)) {
                              handleUpdateTokenStatus(token.id, 'CANCELLED', 'Cancelled by provider');
                            }
                          }}
                          disabled={isUpdating}
                        >
                          ✕ Cancel
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
            </div>
          )}
        </section>
      )}

      {/* ========================================================================= */}
      {/* TAB 3: PROVIDER PROFILE & SCHEDULE MANAGEMENT (Doctor View) */}
      {/* ========================================================================= */}
      {activeTab === 'my-schedule' && currentUser && (currentUser.role === 'PROVIDER' || currentUser.role === 'ADMIN') && (
        <section className="section-card">
          <div className="section-header">
            <div>
              <h2 className="section-title">Provider Profile & Availability Settings</h2>
              <p className="section-subtitle">
                Configure your public practice profile, working days, operating hours, and appointment duration.
              </p>
            </div>
            <span className="role-badge role-badge-provider">{currentUser.role} ACCESS</span>
          </div>

          {profileMsg.text && (
            <div className={`alert ${profileMsg.type === 'error' ? 'alert-error' : 'alert-success'}`}>
              {profileMsg.text}
            </div>
          )}

          {isLoadingProfile ? (
            <div className="loading-state">
              <div className="spinner"></div>
              <p>Loading your schedule settings...</p>
            </div>
          ) : (
            <form onSubmit={handleSaveProfile} className="provider-form">
              <div className="form-row">
                <div className="form-group flex-1">
                  <label className="form-label" htmlFor="displayName">Public Display Name</label>
                  <input
                    id="displayName"
                    type="text"
                    className="form-input"
                    value={providerForm.displayName}
                    onChange={(e) => setProviderForm({ ...providerForm, displayName: e.target.value })}
                    placeholder="e.g. Dr. Sarah Jenkins, MD"
                    required
                  />
                  <p className="form-hint">The professional name patients will see in the clinic directory.</p>
                </div>

                <div className="form-group flex-1">
                  <label className="form-label" htmlFor="serviceDescription">Specialty & Description</label>
                  <input
                    id="serviceDescription"
                    type="text"
                    className="form-input"
                    value={providerForm.serviceDescription}
                    onChange={(e) => setProviderForm({ ...providerForm, serviceDescription: e.target.value })}
                    placeholder="e.g. Obstetrics & Maternal Health"
                  />
                  <p className="form-hint">Brief summary of medical services provided.</p>
                </div>
              </div>

              {/* Working Days Checkbox Selector */}
              <div className="form-group">
                <label className="form-label">Working Days</label>
                <div className="days-selector">
                  {ALL_DAYS_OF_WEEK.map((day) => {
                    const isChecked = providerForm.workingDays.includes(day);
                    return (
                      <label
                        key={day}
                        className={`day-checkbox-label ${isChecked ? 'active' : ''}`}
                      >
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => handleDayToggle(day)}
                        />
                        {day.slice(0, 3)}
                      </label>
                    );
                  })}
                </div>
                <p className="form-hint">Patients will only be able to view and book slots on selected days.</p>
              </div>

              {/* Operating Hours and Slot Duration */}
              <div className="form-row">
                <div className="form-group flex-1">
                  <label className="form-label" htmlFor="openTime">Opening Time (24h)</label>
                  <input
                    id="openTime"
                    type="time"
                    className="form-input"
                    value={providerForm.openTime}
                    onChange={(e) => setProviderForm({ ...providerForm, openTime: e.target.value })}
                    required
                  />
                  <p className="form-hint">Start of consultation shifts.</p>
                </div>

                <div className="form-group flex-1">
                  <label className="form-label" htmlFor="closeTime">Closing Time (24h)</label>
                  <input
                    id="closeTime"
                    type="time"
                    className="form-input"
                    value={providerForm.closeTime}
                    onChange={(e) => setProviderForm({ ...providerForm, closeTime: e.target.value })}
                    required
                  />
                  <p className="form-hint">End of consultation shifts.</p>
                </div>

                <div className="form-group flex-1">
                  <label className="form-label" htmlFor="slotDuration">Consultation Duration</label>
                  <select
                    id="slotDuration"
                    className="form-input"
                    value={providerForm.slotDurationMinutes}
                    onChange={(e) => setProviderForm({ ...providerForm, slotDurationMinutes: parseInt(e.target.value, 10) })}
                  >
                    <option value={15}>15 minutes</option>
                    <option value={20}>20 minutes</option>
                    <option value={30}>30 minutes</option>
                    <option value={45}>45 minutes</option>
                    <option value={60}>60 minutes</option>
                  </select>
                  <p className="form-hint">Determines slot breakdown.</p>
                </div>
              </div>

              {/* Active Toggle Checkbox */}
              <div className="form-group">
                <label className="checkbox-toggle">
                  <input
                    type="checkbox"
                    checked={providerForm.active}
                    onChange={(e) => setProviderForm({ ...providerForm, active: e.target.checked })}
                  />
                  <span>Active Provider Profile (Visible in public clinic directory)</span>
                </label>
              </div>

              <div style={{ marginTop: '1.5rem', display: 'flex', gap: '1rem' }}>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={isSavingProfile}
                >
                  {isSavingProfile ? 'Saving...' : '💾 Save Schedule Changes'}
                </button>
              </div>
            </form>
          )}
        </section>
      )}

      {/* ========================================================================= */}
      {/* TAB: ADMINISTRATOR DASHBOARD (Phase 6) */}
      {/* ========================================================================= */}
      {activeTab === 'admin-dashboard' && (
        <section className="section-card">
          <ErrorBoundary>
          <div className="section-header">
            <div>
              <h2 className="section-title">Clinic Administrator Oversight Dashboard</h2>
              <p className="section-subtitle">
                System-wide metrics, registered user directory, provider credentials, and clinic queue operations.
              </p>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
              <span className="role-badge role-badge-admin">ADMIN PRIVILEGES</span>
              <button
                className="btn btn-secondary"
                onClick={loadAdminDashboard}
                disabled={isLoadingAdmin}
                style={{ fontSize: '0.85rem', padding: '0.45rem 0.85rem' }}
              >
                {isLoadingAdmin ? 'Refreshing...' : `↻ Refresh (${lastAdminRefresh || 'Now'})`}
              </button>
            </div>
          </div>

          {adminError && <div className="alert alert-error">{adminError}</div>}
          {adminSuccessMsg && <div className="alert alert-success">{adminSuccessMsg}</div>}

          {!currentUser || currentUser.role !== 'ADMIN' ? (
            <div className="empty-state">
              <div className="empty-icon">⛔</div>
              <h3>Access Denied: Admin Role Required</h3>
              <p>
                Only authenticated users with the <strong>ADMIN</strong> role can view this dashboard.
                Backend endpoints strictly return HTTP 403 Forbidden for unauthorized requests.
              </p>
            </div>
          ) : isLoadingAdmin && !adminStats ? (
            <div className="loading-state">
              <div className="spinner"></div>
              <p>Loading clinic administrative data...</p>
            </div>
          ) : (
            <div>
              {/* Operational KPI Tiles (Real Database Metrics) */}
              {adminStats && (
                <>
                  <div className="admin-stats-grid">
                    <div className="kpi-card">
                      <span className="kpi-icon">👥</span>
                      <div className="kpi-info">
                        <span className="kpi-number">{adminStats.totalUsers}</span>
                        <span className="kpi-title">Total Users</span>
                      </div>
                    </div>

                    <div className="kpi-card">
                      <span className="kpi-icon">🩺</span>
                      <div className="kpi-info">
                        <span className="kpi-number">{adminStats.totalProviders}</span>
                        <span className="kpi-title">Specialist Doctors</span>
                      </div>
                    </div>

                    <div className="kpi-card">
                      <span className="kpi-icon">📅</span>
                      <div className="kpi-info">
                        <span className="kpi-number">{adminStats.totalAppointments}</span>
                        <span className="kpi-title">Total Bookings</span>
                      </div>
                    </div>

                    <div className="kpi-card kpi-waiting">
                      <span className="kpi-icon">⏳</span>
                      <div className="kpi-info">
                        <span className="kpi-number">{adminStats.waitingTokens}</span>
                        <span className="kpi-title">Waiting in Queue</span>
                      </div>
                    </div>

                    <div className="kpi-card kpi-active">
                      <span className="kpi-icon">📢</span>
                      <div className="kpi-info">
                        <span className="kpi-number">{adminStats.inProgressTokens}</span>
                        <span className="kpi-title">Active / In Room</span>
                      </div>
                    </div>

                    <div className="kpi-card kpi-completed">
                      <span className="kpi-icon">✅</span>
                      <div className="kpi-info">
                        <span className="kpi-number">{adminStats.completedAppointments}</span>
                        <span className="kpi-title">Completed Visits</span>
                      </div>
                    </div>

                    <div className="kpi-card">
                      <span className="metric-icon">🎟️</span>
                      <div className="kpi-info">
                        <span className="kpi-number">{adminStats.totalTokens || 0}</span>
                        <span className="kpi-title">Tokens Generated</span>
                      </div>
                    </div>

                    <div className="kpi-card">
                      <span className="metric-icon">📋</span>
                      <div className="kpi-info">
                        <span className="kpi-number">{adminStats.pendingAppointments || 0}</span>
                        <span className="kpi-title">Pending Appts</span>
                      </div>
                    </div>

                    <div className="kpi-card">
                      <span className="metric-icon">⏱️</span>
                      <div className="kpi-info">
                        <span className="kpi-number">
                          {adminStats.averageWaitMinutes != null ? `${adminStats.averageWaitMinutes}m` : 'N/A'}
                        </span>
                        <span className="kpi-title">Avg Wait Time</span>
                      </div>
                    </div>

                    <div className="kpi-card kpi-cancelled">
                      <span className="kpi-icon">❌</span>
                      <div className="kpi-info">
                        <span className="kpi-number">{adminStats.cancelledAppointments}</span>
                        <span className="kpi-title">Cancelled Visits</span>
                      </div>
                    </div>
                  </div>

                  {adminStats.averageWaitExplanation && (
                    <div style={{ margin: '0.75rem 0 1.25rem 0', padding: '0.65rem 1rem', background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '8px', fontSize: '0.825rem', color: '#475569', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <span>ℹ️</span>
                      <span><strong>Average Waiting Time:</strong> {adminStats.averageWaitDisplay || (adminStats.averageWaitMinutes != null ? `${adminStats.averageWaitMinutes} mins` : 'Not available')}. {adminStats.averageWaitExplanation}</span>
                    </div>
                  )}
                </>
              )}

              {/* Admin Section Tabs */}
              <div className="admin-subtabs">
                <button
                  className={`admin-subtab-btn ${adminSubTab === 'analytics' ? 'active' : ''}`}
                  onClick={() => setAdminSubTab('analytics')}
                >
                  📊 Analytics & Trends
                </button>
                <button
                  className={`admin-subtab-btn ${adminSubTab === 'overview' ? 'active' : ''}`}
                  onClick={() => setAdminSubTab('overview')}
                >
                  📌 Queue Operations ({(adminQueue || []).length})
                </button>
                <button
                  className={`admin-subtab-btn ${adminSubTab === 'users' ? 'active' : ''}`}
                  onClick={() => setAdminSubTab('users')}
                >
                  👥 Users Directory ({(adminUsers || []).length})
                </button>
                <button
                  className={`admin-subtab-btn ${adminSubTab === 'providers' ? 'active' : ''}`}
                  onClick={() => setAdminSubTab('providers')}
                >
                  🩺 Doctors & Schedules ({(adminProviders || []).length})
                </button>
                <button
                  className={`admin-subtab-btn ${adminSubTab === 'appointments' ? 'active' : ''}`}
                  onClick={() => setAdminSubTab('appointments')}
                >
                  📅 All Appointments ({(adminAppointments || []).length})
                </button>
                <button
                  className={`admin-subtab-btn ${adminSubTab === 'book' ? 'active' : ''}`}
                  onClick={() => setAdminSubTab('book')}
                >
                  ➕ Book Appointment
                </button>
              </div>

              {/* Subtab: Analytics & Trends */}
              {adminSubTab === 'analytics' && (
                <div style={{ marginTop: '1rem' }}>
                  {/* Date Range Controls */}
                  <form onSubmit={handleApplyAnalyticsFilter} className="admin-filter-bar">
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <label style={{ fontSize: '0.85rem', fontWeight: 600, color: '#475569' }}>From:</label>
                      <input
                        type="date"
                        className="admin-filter-select"
                        value={analyticsStartDate}
                        onChange={(e) => setAnalyticsStartDate(e.target.value)}
                      />
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <label style={{ fontSize: '0.85rem', fontWeight: 600, color: '#475569' }}>To:</label>
                      <input
                        type="date"
                        className="admin-filter-select"
                        value={analyticsEndDate}
                        onChange={(e) => setAnalyticsEndDate(e.target.value)}
                      />
                    </div>
                    <button type="submit" className="btn btn-primary" style={{ padding: '0.45rem 0.9rem', fontSize: '0.85rem' }}>
                      Apply Filter
                    </button>
                    <button
                      type="button"
                      className="btn btn-secondary"
                      style={{ padding: '0.45rem 0.75rem', fontSize: '0.85rem' }}
                      onClick={() => handleQuickAnalyticsPreset(7)}
                    >
                      Last 7 Days
                    </button>
                    <button
                      type="button"
                      className="btn btn-secondary"
                      style={{ padding: '0.45rem 0.75rem', fontSize: '0.85rem' }}
                      onClick={() => handleQuickAnalyticsPreset(30)}
                    >
                      Last 30 Days
                    </button>
                    <button
                      type="button"
                      className="btn btn-secondary"
                      style={{ padding: '0.45rem 0.75rem', fontSize: '0.85rem' }}
                      onClick={() => handleQuickAnalyticsPreset(0)}
                    >
                      All Time
                    </button>
                  </form>

                  {/* Summary Callout */}
                  <div style={{ background: '#f0f9ff', border: '1px solid #bae6fd', borderRadius: '8px', padding: '0.75rem 1rem', marginBottom: '1.25rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
                    <div>
                      <span style={{ fontSize: '0.85rem', color: '#0369a1', fontWeight: 600 }}>Active Analytics Window: </span>
                      <span style={{ fontSize: '0.85rem', color: '#0c4a6e' }}>
                        {adminAnalytics?.startDate || 'Earliest'} to {adminAnalytics?.endDate || 'Today'}
                      </span>
                    </div>
                    <div>
                      <span style={{ fontSize: '0.85rem', color: '#0369a1', fontWeight: 600 }}>Average Wait In Range: </span>
                      <span style={{ fontSize: '0.95rem', fontWeight: 700, color: '#0284c7' }}>
                        {adminAnalytics?.averageWaitDisplay || 'Not available'}
                      </span>
                    </div>
                  </div>

                  {/* Charts Grid */}
                  <div className="analytics-charts-grid">
                    {/* Chart 1: Daily Booking Statistics */}
                    <div className="analytics-card">
                      <div className="analytics-card-title">
                        <span>📅 Daily Booking Statistics</span>
                        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                          ({Array.isArray(adminAnalytics?.dailyBookings) ? adminAnalytics.dailyBookings.length : 0} days recorded)
                        </span>
                      </div>
                      {(!Array.isArray(adminAnalytics?.dailyBookings) || adminAnalytics.dailyBookings.length === 0) ? (
                        <div style={{ padding: '2rem', textAlign: 'center', color: '#64748b', fontSize: '0.85rem' }}>
                          No daily booking records found for the selected range.
                        </div>
                      ) : (
                        <div>
                          <div className="simple-bar-chart">
                            {(() => {
                              const dailyList = adminAnalytics.dailyBookings;
                              const maxVal = Math.max(...dailyList.map((d) => Number(d.total || d.totalBookings || 0)), 1);
                              return dailyList.map((day, idx) => {
                                const dayDate = String(day.appointment_date || day.date || `Day ${idx + 1}`);
                                const dayLabel = dayDate.length > 5 ? dayDate.slice(5) : dayDate;
                                const dayTotal = Number(day.total || day.totalBookings || 0);
                                const dayCompleted = Number(day.completed || day.completedBookings || 0);
                                const dayCancelled = Number(day.cancelled || day.cancelledBookings || 0);
                                const heightPct = Math.max(12, Math.round((dayTotal / maxVal) * 100));
                                return (
                                  <div
                                    key={dayDate || idx}
                                    className="bar-column"
                                    title={`${dayDate}: ${dayTotal} total (${dayCompleted} completed, ${dayCancelled} cancelled)`}
                                  >
                                    <span className="bar-val-label">{dayTotal}</span>
                                    <div className="bar-fill-wrapper">
                                      <div className="bar-fill" style={{ height: `${heightPct}%` }}></div>
                                    </div>
                                    <span className="bar-axis-label">{dayLabel}</span>
                                  </div>
                                );
                              });
                            })()}
                          </div>
                          <div className="legend-row">
                            <span><span className="legend-dot" style={{ backgroundColor: '#0284c7' }}></span>Total Bookings</span>
                            <span style={{ color: '#64748b' }}>Includes confirmed, completed, and pending visits.</span>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Chart 2: Monthly Booking Trend */}
                    <div className="analytics-card">
                      <div className="analytics-card-title">
                        <span>📈 Monthly Booking Statistics</span>
                        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                          ({(Array.isArray(adminAnalytics?.monthlyBookings) ? adminAnalytics.monthlyBookings.length : (adminAnalytics?.monthlyBookings ? 1 : 0))} months recorded)
                        </span>
                      </div>
                      {(!adminAnalytics?.monthlyBookings || (Array.isArray(adminAnalytics.monthlyBookings) && adminAnalytics.monthlyBookings.length === 0)) ? (
                        <div style={{ padding: '2rem', textAlign: 'center', color: '#64748b', fontSize: '0.85rem' }}>
                          No monthly booking records recorded in database.
                        </div>
                      ) : (
                        <div>
                          <div className="simple-bar-chart">
                            {(() => {
                              const monthlyList = Array.isArray(adminAnalytics.monthlyBookings)
                                ? adminAnalytics.monthlyBookings
                                : [adminAnalytics.monthlyBookings];
                              const maxVal = Math.max(...monthlyList.map((m) => Number(m.total || m.totalBookings || 0)), 1);
                              return monthlyList.map((m, idx) => {
                                const monthLabel = String(m.month_str || m.month || `Month ${idx + 1}`);
                                const monthTotal = Number(m.total || m.totalBookings || 0);
                                const monthCompleted = Number(m.completed || m.completedBookings || 0);
                                const monthCancelled = Number(m.cancelled || m.cancelledBookings || 0);
                                const heightPct = Math.max(12, Math.round((monthTotal / maxVal) * 100));
                                return (
                                  <div
                                    key={monthLabel || idx}
                                    className="bar-column"
                                    title={`${monthLabel}: ${monthTotal} bookings (${monthCompleted} completed, ${monthCancelled} cancelled)`}
                                  >
                                    <span className="bar-val-label">{monthTotal}</span>
                                    <div className="bar-fill-wrapper">
                                      <div className="bar-fill bar-completed" style={{ height: `${heightPct}%` }}></div>
                                    </div>
                                    <span className="bar-axis-label">{monthLabel}</span>
                                  </div>
                                );
                              });
                            })()}
                          </div>
                          <div className="legend-row">
                            <span><span className="legend-dot" style={{ backgroundColor: '#16a34a' }}></span>Monthly Total Visits</span>
                            <span style={{ color: '#64748b' }}>Longitudinal volume across months</span>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Chart 3: Queue Performance Trends */}
                    <div className="analytics-card" style={{ gridColumn: '1 / -1' }}>
                      <div className="analytics-card-title">
                        <span>⏱️ Queue Performance Trends & Service Volume</span>
                        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>Token generation, patient throughput & wait metrics</span>
                      </div>
                      {(!Array.isArray(adminAnalytics?.queueTrends) || adminAnalytics.queueTrends.length === 0) ? (
                        <div style={{ padding: '2rem', textAlign: 'center', color: '#64748b', fontSize: '0.85rem' }}>
                          No queue tokens recorded for the selected period.
                        </div>
                      ) : (
                        <div>
                          <div className="simple-bar-chart">
                            {(() => {
                              const queueList = adminAnalytics.queueTrends;
                              const maxTokens = Math.max(...queueList.map((q) => Number(q.total_tokens || q.totalTokens || 0)), 1);
                              return queueList.map((q, idx) => {
                                const qDate = String(q.appointment_date || q.date || `Day ${idx + 1}`);
                                const qLabel = qDate.length > 5 ? qDate.slice(5) : qDate;
                                const totalTokens = Number(q.total_tokens || q.totalTokens || 0);
                                const servedTokens = Number(q.served || q.servedTokens || 0);
                                const totalHeight = Math.max(12, Math.round((totalTokens / maxTokens) * 100));
                                const servedHeight = Math.max(8, Math.round((servedTokens / maxTokens) * 100));
                                const avgWait = q.avg_wait != null
                                  ? Math.round(Number(q.avg_wait) * 10) / 10
                                  : (q.averageWaitMinutes != null ? Math.round(Number(q.averageWaitMinutes) * 10) / 10 : null);

                                return (
                                  <div
                                    key={qDate || idx}
                                    className="bar-column"
                                    title={`${qDate}: ${totalTokens} tokens generated, ${servedTokens} served, avg wait: ${avgWait != null ? avgWait + 'm' : 'N/A'}`}
                                  >
                                    <span className="bar-val-label">
                                      {totalTokens} <small style={{ color: '#7c3aed' }}>({servedTokens} served)</small>
                                    </span>
                                    <div className="bar-fill-wrapper" style={{ gap: '4px' }}>
                                      <div className="bar-fill" style={{ height: `${totalHeight}%`, width: '16px' }}></div>
                                      <div className="bar-fill bar-served" style={{ height: `${servedHeight}%`, width: '16px' }}></div>
                                    </div>
                                    <span className="bar-axis-label">{qLabel}</span>
                                    {avgWait != null && (
                                      <span style={{ fontSize: '0.65rem', color: '#0284c7', fontWeight: 600 }}>~{avgWait}m</span>
                                    )}
                                  </div>
                                );
                              });
                            })()}
                          </div>
                          <div className="legend-row">
                            <span><span className="legend-dot" style={{ backgroundColor: '#0284c7' }}></span>Tokens Generated</span>
                            <span><span className="legend-dot" style={{ backgroundColor: '#7c3aed' }}></span>Patients Served</span>
                            <span style={{ color: '#64748b' }}>Blue label shows average wait time per date where patients were called</span>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              )}

              {/* Subtab: Queue Activity & Operations */}
              {adminSubTab === 'overview' && (() => {
                const filtered = (adminQueue || []).filter((t) => {
                  const matchesStatus = queueStatusFilter === 'ALL' || t?.status === queueStatusFilter;
                  const matchesSearch = !queueSearchQuery ||
                    String(t?.tokenDisplay || '').includes(queueSearchQuery) ||
                    t?.customerName?.toLowerCase().includes(queueSearchQuery.toLowerCase()) ||
                    t?.customerEmail?.toLowerCase().includes(queueSearchQuery.toLowerCase()) ||
                    t?.providerName?.toLowerCase().includes(queueSearchQuery.toLowerCase());
                  return matchesStatus && matchesSearch;
                });
                const PAGE_SIZE = 10;
                const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
                const currentPage = Math.min(queuePage, totalPages);
                const paged = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

                return (
                  <div style={{ marginTop: '1rem' }}>
                    <div className="admin-filter-bar">
                      <input
                        type="text"
                        placeholder="Search token, patient name, doctor..."
                        className="admin-search-input"
                        value={queueSearchQuery}
                        onChange={(e) => { setQueueSearchQuery(e.target.value); setQueuePage(1); }}
                      />
                      <select
                        className="admin-filter-select"
                        value={queueStatusFilter}
                        onChange={(e) => { setQueueStatusFilter(e.target.value); setQueuePage(1); }}
                      >
                        <option value="ALL">All Token Statuses</option>
                        <option value="WAITING">WAITING</option>
                        <option value="CALLED">CALLED</option>
                        <option value="IN_PROGRESS">IN_PROGRESS</option>
                        <option value="COMPLETED">COMPLETED</option>
                        <option value="SKIPPED">SKIPPED</option>
                        <option value="CANCELLED">CANCELLED</option>
                      </select>
                      <span style={{ fontSize: '0.85rem', color: '#64748b', marginLeft: 'auto' }}>
                        Showing {paged.length} of {filtered.length} tokens
                      </span>
                    </div>

                    <div className="admin-table-container">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>Token</th>
                            <th>Status</th>
                            <th>Doctor</th>
                            <th>Patient</th>
                            <th>Date & Window</th>
                            <th>Wait / Timestamps</th>
                            <th>Admin Operations</th>
                          </tr>
                        </thead>
                        <tbody>
                          {paged.length === 0 ? (
                            <tr><td colSpan="7" style={{ textAlign: 'center', padding: '1.5rem', color: '#64748b' }}>No matching queue tokens found.</td></tr>
                          ) : (
                            paged.map((t) => {
                              const waitMinutes = (t.calledAt && t.createdAt)
                                ? Math.max(0, Math.round((new Date(t.calledAt) - new Date(t.createdAt)) / 60000))
                                : null;
                              return (
                                <tr key={t.id}>
                                  <td><strong>#{t.tokenDisplay}</strong></td>
                                  <td><span className={`status-badge status-badge-${t.status.toLowerCase()}`}>{t.status}</span></td>
                                  <td>{t.providerName}</td>
                                  <td>{t.customerName} <small style={{ color: '#64748b' }}>({t.customerEmail})</small></td>
                                  <td>{t.appointmentDate} • {t.startTime}-{t.endTime}</td>
                                  <td>
                                    {waitMinutes != null ? (
                                      <span style={{ color: '#0284c7', fontWeight: 600 }}>Waited {waitMinutes}m</span>
                                    ) : t.status === 'WAITING' ? (
                                      <span style={{ color: '#d97706' }}>In queue since {t.createdAt ? new Date(t.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '—'}</span>
                                    ) : (
                                      <span style={{ color: '#64748b' }}>—</span>
                                    )}
                                  </td>
                                  <td>
                                    <div style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap' }}>
                                      {t.status === 'WAITING' && (
                                        <button className="btn btn-primary" style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem' }} onClick={() => handleAdminUpdateTokenStatus(t.id, 'CALLED')}>
                                          📢 Call
                                        </button>
                                      )}
                                      {t.status === 'CALLED' && (
                                        <button className="btn btn-primary" style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem' }} onClick={() => handleAdminUpdateTokenStatus(t.id, 'IN_PROGRESS')}>
                                          ▶️ In Room
                                        </button>
                                      )}
                                      {t.status === 'IN_PROGRESS' && (
                                        <button className="btn btn-success" style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem' }} onClick={() => handleAdminUpdateTokenStatus(t.id, 'COMPLETED')}>
                                          ✅ Complete
                                        </button>
                                      )}
                                      {(t.status === 'WAITING' || t.status === 'CALLED') && (
                                        <button className="btn btn-secondary" style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem' }} onClick={() => handleAdminUpdateTokenStatus(t.id, 'SKIPPED')}>
                                          ⏭️ Skip
                                        </button>
                                      )}
                                      {t.status !== 'COMPLETED' && t.status !== 'CANCELLED' && (
                                        <button className="btn btn-danger" style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem' }} onClick={() => handleAdminUpdateTokenStatus(t.id, 'CANCELLED')}>
                                          ✕ Cancel
                                        </button>
                                      )}
                                    </div>
                                  </td>
                                </tr>
                              );
                            })
                          )}
                        </tbody>
                      </table>
                    </div>

                    {totalPages > 1 && (
                      <div className="admin-pagination">
                        <button className="btn-page" disabled={currentPage <= 1} onClick={() => setQueuePage(currentPage - 1)}>
                          ← Previous
                        </button>
                        <span>Page {currentPage} of {totalPages}</span>
                        <button className="btn-page" disabled={currentPage >= totalPages} onClick={() => setQueuePage(currentPage + 1)}>
                          Next →
                        </button>
                      </div>
                    )}
                  </div>
                );
              })()}

              {/* Subtab: Users Directory */}
              {adminSubTab === 'users' && (() => {
                const filtered = (adminUsers || []).filter((u) => {
                  const matchesRole = userRoleFilter === 'ALL' || u?.role === userRoleFilter;
                  const matchesSearch = !userSearchQuery ||
                    String(u?.id || '').includes(userSearchQuery) ||
                    u?.name?.toLowerCase().includes(userSearchQuery.toLowerCase()) ||
                    u?.email?.toLowerCase().includes(userSearchQuery.toLowerCase());
                  return matchesRole && matchesSearch;
                });
                const PAGE_SIZE = 10;
                const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
                const currentPage = Math.min(userPage, totalPages);
                const paged = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

                return (
                  <div style={{ marginTop: '1rem' }}>
                    <div className="admin-filter-bar">
                      <input
                        type="text"
                        placeholder="Search by name, email, or user ID..."
                        className="admin-search-input"
                        value={userSearchQuery}
                        onChange={(e) => { setUserSearchQuery(e.target.value); setUserPage(1); }}
                      />
                      <select
                        className="admin-filter-select"
                        value={userRoleFilter}
                        onChange={(e) => { setUserRoleFilter(e.target.value); setUserPage(1); }}
                      >
                        <option value="ALL">All Assigned Roles</option>
                        <option value="ADMIN">ADMIN</option>
                        <option value="PROVIDER">PROVIDER</option>
                        <option value="CUSTOMER">CUSTOMER</option>
                      </select>
                      <span style={{ fontSize: '0.85rem', color: '#64748b', marginLeft: 'auto' }}>
                        Showing {paged.length} of {filtered.length} users
                      </span>
                    </div>

                    <div className="admin-table-container">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>User ID</th>
                            <th>Full Name</th>
                            <th>Email Address</th>
                            <th>Assigned Role</th>
                            <th>Registered At</th>
                            <th>Manage Role</th>
                          </tr>
                        </thead>
                        <tbody>
                          {paged.length === 0 ? (
                            <tr><td colSpan="6" style={{ textAlign: 'center', padding: '1.5rem', color: '#64748b' }}>No matching registered users found.</td></tr>
                          ) : (
                            paged.map((u) => {
                              const isSelf = currentUser && currentUser.id === u.id;
                              return (
                                <tr key={u.id}>
                                  <td>#{u.id}</td>
                                  <td><strong>{u.name}</strong> {isSelf && <small style={{ color: '#0284c7' }}>(You)</small>}</td>
                                  <td>{u.email}</td>
                                  <td>
                                    <span className={`role-badge role-badge-${u.role.toLowerCase()}`}>
                                      {u.role}
                                    </span>
                                  </td>
                                  <td>{u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '—'}</td>
                                  <td>
                                    {isSelf ? (
                                      <span style={{ fontSize: '0.75rem', color: '#64748b' }}>Active Session</span>
                                    ) : (
                                      <select
                                        className="admin-filter-select"
                                        style={{ fontSize: '0.8rem', padding: '0.25rem 0.5rem' }}
                                        value={u.role}
                                        onChange={(e) => {
                                          const newRole = e.target.value;
                                          if (window.confirm(`Update ${u.name}'s role from ${u.role} to ${newRole}?`)) {
                                            handleUpdateUserRoleAction(u.id, newRole);
                                          }
                                        }}
                                      >
                                        <option value="CUSTOMER">CUSTOMER</option>
                                        <option value="PROVIDER">PROVIDER</option>
                                        <option value="ADMIN">ADMIN</option>
                                      </select>
                                    )}
                                  </td>
                                </tr>
                              );
                            })
                          )}
                        </tbody>
                      </table>
                    </div>

                    {totalPages > 1 && (
                      <div className="admin-pagination">
                        <button className="btn-page" disabled={currentPage <= 1} onClick={() => setUserPage(currentPage - 1)}>
                          ← Previous
                        </button>
                        <span>Page {currentPage} of {totalPages}</span>
                        <button className="btn-page" disabled={currentPage >= totalPages} onClick={() => setUserPage(currentPage + 1)}>
                          Next →
                        </button>
                      </div>
                    )}
                  </div>
                );
              })()}

              {/* Subtab: Providers & Schedules */}
              {adminSubTab === 'providers' && (() => {
                const filtered = (adminProviders || []).filter((p) => {
                  const matchesStatus = providerStatusFilter === 'ALL' ||
                    (providerStatusFilter === 'ACTIVE' ? p?.active : !p?.active);
                  const matchesSearch = !providerSearchQuery ||
                    String(p?.id || '').includes(providerSearchQuery) ||
                    p?.displayName?.toLowerCase().includes(providerSearchQuery.toLowerCase()) ||
                    p?.serviceDescription?.toLowerCase().includes(providerSearchQuery.toLowerCase());
                  return matchesStatus && matchesSearch;
                });
                const PAGE_SIZE = 10;
                const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
                const currentPage = Math.min(providerPage, totalPages);
                const paged = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

                return (
                  <div style={{ marginTop: '1rem' }}>
                    <div className="admin-filter-bar">
                      <input
                        type="text"
                        placeholder="Search doctor name or specialty..."
                        className="admin-search-input"
                        value={providerSearchQuery}
                        onChange={(e) => { setProviderSearchQuery(e.target.value); setProviderPage(1); }}
                      />
                      <select
                        className="admin-filter-select"
                        value={providerStatusFilter}
                        onChange={(e) => { setProviderStatusFilter(e.target.value); setProviderPage(1); }}
                      >
                        <option value="ALL">All Provider Statuses</option>
                        <option value="ACTIVE">ACTIVE Only</option>
                        <option value="INACTIVE">DEACTIVATED Only</option>
                      </select>
                      <span style={{ fontSize: '0.85rem', color: '#64748b', marginLeft: 'auto' }}>
                        Showing {paged.length} of {filtered.length} doctors
                      </span>
                    </div>

                    <div className="admin-table-container">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>ID</th>
                            <th>Doctor Name</th>
                            <th>Specialty Description</th>
                            <th>Working Days</th>
                            <th>Hours & Slot</th>
                            <th>Status</th>
                            <th>Admin Oversight</th>
                          </tr>
                        </thead>
                        <tbody>
                          {paged.length === 0 ? (
                            <tr><td colSpan="7" style={{ textAlign: 'center', padding: '1.5rem', color: '#64748b' }}>No matching providers found.</td></tr>
                          ) : (
                            paged.map((p) => (
                              <tr key={p.id}>
                                <td>#{p.id}</td>
                                <td><strong>{p.displayName}</strong></td>
                                <td>{p.serviceDescription || 'General Medicine'}</td>
                                <td>{p.workingDays ? p.workingDays.join(', ') : 'None'}</td>
                                <td>{p.openTime} - {p.closeTime} ({p.slotDurationMinutes}m)</td>
                                <td>
                                  <span className={`status-badge ${p.active ? 'status-badge-completed' : 'status-badge-cancelled'}`}>
                                    {p.active ? 'Active' : 'Inactive'}
                                  </span>
                                </td>
                                <td>
                                  <button
                                    className={`btn ${p.active ? 'btn-danger' : 'btn-success'}`}
                                    style={{ padding: '0.25rem 0.65rem', fontSize: '0.75rem' }}
                                    onClick={() => handleToggleProviderAction(p.id)}
                                  >
                                    {p.active ? 'Deactivate' : 'Activate'}
                                  </button>
                                </td>
                              </tr>
                            ))
                          )}
                        </tbody>
                      </table>
                    </div>

                    {totalPages > 1 && (
                      <div className="admin-pagination">
                        <button className="btn-page" disabled={currentPage <= 1} onClick={() => setProviderPage(currentPage - 1)}>
                          ← Previous
                        </button>
                        <span>Page {currentPage} of {totalPages}</span>
                        <button className="btn-page" disabled={currentPage >= totalPages} onClick={() => setProviderPage(currentPage + 1)}>
                          Next →
                        </button>
                      </div>
                    )}
                  </div>
                );
              })()}

              {/* Subtab: All Appointments Oversight */}
              {adminSubTab === 'appointments' && (() => {
                const filtered = (adminAppointments || []).filter((a) => {
                  const matchesStatus = apptStatusFilter === 'ALL' || a?.status === apptStatusFilter;
                  const matchesSearch = !apptSearchQuery ||
                    String(a?.id || '').includes(apptSearchQuery) ||
                    String(a?.tokenDisplay || '').includes(apptSearchQuery) ||
                    a?.customerName?.toLowerCase().includes(apptSearchQuery.toLowerCase()) ||
                    a?.providerName?.toLowerCase().includes(apptSearchQuery.toLowerCase());
                  return matchesStatus && matchesSearch;
                });
                const PAGE_SIZE = 10;
                const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
                const currentPage = Math.min(apptPage, totalPages);
                const paged = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

                return (
                  <div style={{ marginTop: '1rem' }}>
                    <div className="admin-filter-bar">
                      <input
                        type="text"
                        placeholder="Search patient, doctor, token #..."
                        className="admin-search-input"
                        value={apptSearchQuery}
                        onChange={(e) => { setApptSearchQuery(e.target.value); setApptPage(1); }}
                      />
                      <select
                        className="admin-filter-select"
                        value={apptStatusFilter}
                        onChange={(e) => { setApptStatusFilter(e.target.value); setApptPage(1); }}
                      >
                        <option value="ALL">All Appointment Statuses</option>
                        <option value="PENDING">PENDING</option>
                        <option value="CONFIRMED">CONFIRMED</option>
                        <option value="COMPLETED">COMPLETED</option>
                        <option value="CANCELLED">CANCELLED</option>
                      </select>
                      <span style={{ fontSize: '0.85rem', color: '#64748b', marginLeft: 'auto' }}>
                        Showing {paged.length} of {filtered.length} appointments
                      </span>
                    </div>

                    <div className="admin-table-container">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>ID</th>
                            <th>Patient</th>
                            <th>Doctor</th>
                            <th>Date</th>
                            <th>Time Window</th>
                            <th>Token</th>
                            <th>Status</th>
                            <th>Admin Actions</th>
                          </tr>
                        </thead>
                        <tbody>
                          {paged.length === 0 ? (
                            <tr><td colSpan="8" style={{ textAlign: 'center', padding: '1.5rem', color: '#64748b' }}>No matching appointments found.</td></tr>
                          ) : (
                            paged.map((a) => (
                              <tr key={a.id}>
                                <td>#{a.id}</td>
                                <td>{a.customerName}</td>
                                <td>{a.providerName}</td>
                                <td>{a.appointmentDate}</td>
                                <td>{a.startTime} – {a.endTime}</td>
                                <td>{a.tokenDisplay ? `#${a.tokenDisplay}` : '—'}</td>
                                <td><span className={`status-badge status-badge-${a.status.toLowerCase()}`}>{a.status}</span></td>
                                <td>
                                  <div style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap' }}>
                                    {a.status === 'PENDING' && (
                                      <button className="btn btn-primary" style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem' }} onClick={() => handleAdminConfirmAppt(a.id)}>
                                        Confirm
                                      </button>
                                    )}
                                    {(a.status === 'PENDING' || a.status === 'CONFIRMED') && (
                                      <button className="btn btn-success" style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem' }} onClick={() => handleAdminCompleteAppt(a.id)}>
                                        Complete
                                      </button>
                                    )}
                                    {a.status !== 'COMPLETED' && a.status !== 'CANCELLED' && (
                                      <button className="btn btn-danger" style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem' }} onClick={() => handleAdminCancelAppt(a.id)}>
                                        Cancel
                                      </button>
                                    )}
                                    <button className="btn btn-secondary" style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem' }} onClick={() => handleViewHistory(a.id)}>
                                      History
                                    </button>
                                  </div>
                                </td>
                              </tr>
                            ))
                          )}
                        </tbody>
                      </table>
                    </div>

                    {totalPages > 1 && (
                      <div className="admin-pagination">
                        <button className="btn-page" disabled={currentPage <= 1} onClick={() => setApptPage(currentPage - 1)}>
                          ← Previous
                        </button>
                        <span>Page {currentPage} of {totalPages}</span>
                        <button className="btn-page" disabled={currentPage >= totalPages} onClick={() => setApptPage(currentPage + 1)}>
                          Next →
                        </button>
                      </div>
                    )}
                  </div>
                );
              })()}

              {/* Subtab 5: Admin Book Appointment for Themselves */}
              {adminSubTab === 'book' && (
                <div style={{ marginTop: '1.25rem' }}>
                  <BookingFlow
                    currentUser={currentUser}
                    providers={providers}
                    bookableProviders={bookableProviders}
                    selectedProvider={selectedProvider}
                    setSelectedProvider={setSelectedProvider}
                    selectedDate={selectedDate}
                    setSelectedDate={setSelectedDate}
                    slotData={slotData}
                    isLoadingSlots={isLoadingSlots}
                    slotError={slotError}
                    selectedSlot={selectedSlot}
                    setSelectedSlot={setSelectedSlot}
                    bookingNotes={bookingNotes}
                    setBookingNotes={setBookingNotes}
                    isBooking={isBooking}
                    bookingSuccessMsg={bookingSuccessMsg}
                    bookingErrorMsg={bookingErrorMsg}
                    providerListError={providerListError}
                    isLoadingProviders={isLoadingProviders}
                    loadProviders={loadProviders}
                    handleConfirmBooking={handleConfirmBooking}
                    onGoToLogin={() => setActiveTab('auth')}
                    onReloadSlots={() => selectedProvider && loadSlots(selectedProvider.id, selectedDate)}
                    lastBookedToken={lastBookedToken}
                    setLastBookedToken={setLastBookedToken}
                    onViewMyAppointments={() => setActiveTab('my-appointments')}
                    bannerNotice="🛡️ Administrator Appointment Booking: Book an appointment for yourself with any specialist doctor. You will be registered as the patient using your administrator account details."
                  />
                </div>
              )}
            </div>
          )}
          </ErrorBoundary>
        </section>
      )}

      {/* ========================================================================= */}
      {/* TAB 4: AUTHENTICATION & USER PROFILE */}
      {/* ========================================================================= */}
      {activeTab === 'auth' && (
        <section className="auth-card">
          {/* Notifications */}
          {authSuccess && <div className="alert alert-success">{authSuccess}</div>}
          {authError && <div className="alert alert-error">{authError}</div>}

          {currentUser ? (
            /* Logged In View */
            <div>
              <div className="profile-header">
                <div>
                  <h2 className="profile-name">Hello, {currentUser.name}!</h2>
                  <p style={{ color: '#64748b', fontSize: '0.9rem' }}>{currentUser.email}</p>
                </div>

                <div>
                  {currentUser.role === 'CUSTOMER' && (
                    <span className="role-badge role-badge-customer">CUSTOMER</span>
                  )}
                  {currentUser.role === 'PROVIDER' && (
                    <span className="role-badge role-badge-provider">PROVIDER</span>
                  )}
                  {currentUser.role === 'ADMIN' && (
                    <span className="role-badge role-badge-admin">ADMIN</span>
                  )}
                </div>
              </div>

              <div className="profile-box">
                <div className="detail-row">
                  <span className="detail-label">Account ID:</span>
                  <span className="detail-value">#{currentUser.id}</span>
                </div>
                <div className="detail-row">
                  <span className="detail-label">Assigned Role:</span>
                  <span className="detail-value">{currentUser.role}</span>
                </div>

                <div
                  className="role-info-card"
                  style={{
                    backgroundColor:
                      currentUser.role === 'PROVIDER'
                        ? '#ecfdf5'
                        : currentUser.role === 'ADMIN'
                        ? '#faf5ff'
                        : '#eff6ff',
                    border:
                      currentUser.role === 'PROVIDER'
                        ? '1px solid #a7f3d0'
                        : currentUser.role === 'ADMIN'
                        ? '1px solid #e9d5ff'
                        : '1px solid #bfdbfe',
                  }}
                >
                  {currentUser.role === 'CUSTOMER' && (
                    <p>
                      <strong>Patient Access:</strong> You can book appointments, view your visit history, and cancel scheduled appointments.
                    </p>
                  )}
                  {currentUser.role === 'PROVIDER' && (
                    <p>
                      <strong>Healthcare Provider Access:</strong> You can manage your practice hours and consultation schedule.
                    </p>
                  )}
                  {currentUser.role === 'ADMIN' && (
                    <p>
                      <strong>Clinic Administrator Access:</strong> Full management privileges across all doctors, patient appointments, and system settings.
                    </p>
                  )}
                </div>
              </div>

              <div style={{ display: 'flex', gap: '1rem', marginTop: '1.25rem' }}>
                <button className="btn btn-danger" onClick={handleLogout}>
                  Log Out
                </button>
              </div>
            </div>
          ) : (
            /* Logged Out View */
            <div>
              <div className="auth-tabs">
                <button
                  className={`tab-btn ${authTab === 'login' ? 'active' : ''}`}
                  onClick={() => {
                    setAuthTab('login');
                    setAuthError('');
                    setAuthSuccess('');
                  }}
                >
                  Log In
                </button>
                <button
                  className={`tab-btn ${authTab === 'register' ? 'active' : ''}`}
                  onClick={() => {
                    setAuthTab('register');
                    setAuthError('');
                    setAuthSuccess('');
                  }}
                >
                  Register
                </button>
              </div>

              {authTab === 'login' ? (
                <form onSubmit={handleLogin}>
                  <div className="form-group">
                    <label className="form-label" htmlFor="loginEmail">Email Address</label>
                    <input
                      id="loginEmail"
                      type="email"
                      className="form-input"
                      placeholder="name@example.com"
                      value={loginEmail}
                      onChange={(e) => setLoginEmail(e.target.value)}
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="loginPassword">Password</label>
                    <input
                      id="loginPassword"
                      type="password"
                      className="form-input"
                      placeholder="Enter your password"
                      value={loginPassword}
                      onChange={(e) => setLoginPassword(e.target.value)}
                      required
                    />
                  </div>

                  <button
                    type="submit"
                    className="btn btn-primary"
                    style={{ width: '100%', marginTop: '0.5rem' }}
                    disabled={isSubmitting}
                  >
                    {isSubmitting ? 'Signing in...' : 'Sign In'}
                  </button>
                </form>
              ) : (
                <form onSubmit={handleRegister}>
                  <div className="form-group">
                    <label className="form-label" htmlFor="regName">Full Name</label>
                    <input
                      id="regName"
                      type="text"
                      className="form-input"
                      placeholder="e.g. Sarah Jenkins"
                      value={regName}
                      onChange={(e) => setRegName(e.target.value)}
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="regEmail">Email Address</label>
                    <input
                      id="regEmail"
                      type="email"
                      className="form-input"
                      placeholder="name@example.com"
                      value={regEmail}
                      onChange={(e) => setRegEmail(e.target.value)}
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="regPassword">Password</label>
                    <input
                      id="regPassword"
                      type="password"
                      className="form-input"
                      placeholder="Minimum 6 characters"
                      value={regPassword}
                      onChange={(e) => setRegPassword(e.target.value)}
                      required
                    />
                    <p className="form-hint">
                      Security note: Public registrations always assign the <strong>CUSTOMER</strong> role.
                    </p>
                  </div>

                  <button
                    type="submit"
                    className="btn btn-primary"
                    style={{ width: '100%', marginTop: '0.5rem' }}
                    disabled={isSubmitting}
                  >
                    {isSubmitting ? 'Creating account...' : 'Create Account'}
                  </button>
                </form>
              )}
            </div>
          )}
        </section>
      )}
        </div>
      )}
    </div>
  );
}

export default App;
