// Base URL of the Spring Boot backend server (defaults to live Render backend in production, localhost in development)
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || (import.meta.env.PROD ? 'https://carequeue-backend-0f3l.onrender.com' : 'http://localhost:8080');

const TOKEN_KEY = 'carequeue_auth_token';

// ==============================================================================
// Token Storage Helpers (Stored in localStorage for development demo)
// ==============================================================================

export function getStoredToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function saveToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  }
}

export function clearStoredToken() {
  localStorage.removeItem(TOKEN_KEY);
}

// ==============================================================================
// Health Check API
// ==============================================================================

/**
 * Checks the health status of the Spring Boot backend.
 */
export async function checkBackendHealth() {
  try {
    const response = await fetch(`${API_BASE_URL}/api/health`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
      },
    });

    if (!response.ok) {
      throw new Error(`Server returned HTTP ${response.status}`);
    }

    const data = await response.json();
    return {
      isOnline: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      isOnline: false,
      data: null,
      error: error.message || 'Unable to connect to the backend server',
    };
  }
}

// ==============================================================================
// Authentication APIs (Phase 2)
// ==============================================================================

/**
 * Registers a new user account with specified role (defaults to CUSTOMER).
 */
export async function registerUser(name, email, password, role = 'CUSTOMER') {
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/register`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify({ name, email, password, role }),
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        error: data.message || `Registration failed (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
    };
  } catch (error) {
    return {
      success: false,
      error: error.message || 'Network error during registration',
    };
  }
}

/**
 * Logs in with user credentials and receives a JWT token.
 */
export async function loginUser(email, password) {
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify({ email, password }),
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        error: data.message || `Login failed (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
    };
  } catch (error) {
    return {
      success: false,
      error: error.message || 'Network error during login',
    };
  }
}

/**
 * Retrieves the currently authenticated user's profile using the stored JWT token.
 */
export async function fetchCurrentUser() {
  const token = getStoredToken();
  if (!token) {
    return {
      authenticated: false,
      user: null,
      error: 'No active session token',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    if (!response.ok) {
      clearStoredToken();
      return {
        authenticated: false,
        user: null,
        error: 'Session expired or token invalid',
      };
    }

    const userData = await response.json();
    return {
      authenticated: true,
      user: userData,
      error: null,
    };
  } catch (error) {
    return {
      authenticated: false,
      user: null,
      error: error.message || 'Failed to verify session',
    };
  }
}

// ==============================================================================
// Provider & Availability APIs (Phase 3)
// ==============================================================================

/**
 * Fetches all active healthcare providers available for booking (Public).
 */
export async function fetchActiveProviders() {
  try {
    const response = await fetch(`${API_BASE_URL}/api/providers`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
      },
    });

    if (!response.ok) {
      throw new Error(`Failed to load providers (HTTP ${response.status})`);
    }

    const data = await response.json();
    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: [],
      error: error.message || 'Unable to fetch providers list',
    };
  }
}

/**
 * Fetches calculated available time slots for a specific provider on a selected date (Public).
 */
export async function fetchProviderSlots(providerId, dateString) {
  try {
    const response = await fetch(`${API_BASE_URL}/api/providers/${providerId}/slots?date=${encodeURIComponent(dateString)}`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Failed to fetch slots (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Unable to fetch time slots',
    };
  }
}

/**
 * Fetches the currently authenticated provider's own profile and availability schedule (Provider/Admin only).
 */
export async function fetchMyProviderProfile() {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: null,
      error: 'Authentication token required',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/provider/me`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Failed to fetch provider profile (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Network error fetching provider profile',
    };
  }
}

/**
 * Updates the provider's profile information and working schedule (Provider/Admin only).
 */
export async function updateMyProviderProfile(profileData) {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: null,
      error: 'Authentication token required',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/provider/me`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify(profileData),
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Failed to update provider profile (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Network error updating provider profile',
    };
  }
}

// ==============================================================================
// Appointment Booking & Cancellation APIs (Phase 4)
// ==============================================================================

/**
 * Books an available appointment slot for the authenticated customer.
 */
export async function bookAppointment(bookingData) {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: null,
      error: 'Please log in to book an appointment',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/appointments`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify(bookingData),
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Booking failed (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Network error while booking appointment',
    };
  }
}

/**
 * Retrieves all appointments booked by the authenticated customer.
 */
export async function fetchMyAppointments() {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: [],
      error: 'Please log in to view appointments',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/appointments/my`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: [],
        error: data.message || `Failed to fetch appointments (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: [],
      error: error.message || 'Network error fetching appointments',
    };
  }
}

/**
 * Retrieves a single appointment by ID.
 */
export async function fetchAppointmentById(appointmentId) {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: null,
      error: 'Please log in to view appointment details',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/appointments/${appointmentId}`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Failed to fetch appointment (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Network error fetching appointment',
    };
  }
}

/**
 * Updates permitted appointment details (notes and/or rescheduled slot).
 */
export async function updateAppointmentDetails(appointmentId, updateData) {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: null,
      error: 'Please log in to update appointment',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/appointments/${appointmentId}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: JSON.stringify(updateData),
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Failed to update appointment (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Network error updating appointment',
    };
  }
}

/**
 * Cancels an eligible appointment booked by the authenticated customer.
 */
export async function cancelAppointment(appointmentId, reason) {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: null,
      error: 'Authentication token required',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/appointments/${appointmentId}/cancel`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify({ reason: reason || 'Cancelled by customer' }),
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Failed to cancel appointment (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Network error cancelling appointment',
    };
  }
}

/**
 * Confirms a pending appointment (callable by assigned provider or admin).
 */
export async function confirmAppointment(appointmentId) {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: null,
      error: 'Authentication token required',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/appointments/${appointmentId}/confirm`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Failed to confirm appointment (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Network error confirming appointment',
    };
  }
}

/**
 * Marks an appointment as COMPLETED (callable by assigned provider or admin).
 */
export async function completeAppointment(appointmentId) {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: null,
      error: 'Authentication token required',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/appointments/${appointmentId}/complete`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Failed to complete appointment (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Network error completing appointment',
    };
  }
}

/**
 * Updates an appointment's status (PENDING, CONFIRMED, COMPLETED, CANCELLED).
 */
export async function updateAppointmentStatus(appointmentId, status, reason = '') {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: null,
      error: 'Authentication token required',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/appointments/${appointmentId}/status`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify({ status, reason }),
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Failed to update appointment status (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Network error updating appointment status',
    };
  }
}

/**
 * Retrieves the status transition audit history for an appointment.
 */
export async function fetchAppointmentHistory(appointmentId) {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: [],
      error: 'Authentication token required',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/appointments/${appointmentId}/history`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: [],
        error: data.message || `Failed to fetch history (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: [],
      error: error.message || 'Network error fetching appointment history',
    };
  }
}

// ==============================================================================
// Provider Clinic Queue APIs (Phase 5)
// ==============================================================================

/**
 * Retrieves the daily consultation queue for a provider on a specific date (defaults to today).
 */
export async function fetchProviderQueue(date) {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: [],
      error: 'Authentication token required',
    };
  }

  try {
    const url = date
      ? `${API_BASE_URL}/api/queue/provider?date=${encodeURIComponent(date)}`
      : `${API_BASE_URL}/api/queue/provider`;

    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: [],
        error: data.message || `Failed to fetch clinic queue (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: [],
      error: error.message || 'Network error fetching clinic queue',
    };
  }
}

/**
 * Calls the next waiting patient in the provider's queue.
 * Transitions token from WAITING -> CALLED.
 */
export async function callNextQueueToken(date) {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: null,
      error: 'Authentication token required',
    };
  }

  try {
    const url = date
      ? `${API_BASE_URL}/api/queue/call-next?date=${encodeURIComponent(date)}`
      : `${API_BASE_URL}/api/queue/call-next`;

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Failed to call next patient (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Network error calling next patient',
    };
  }
}

/**
 * Updates a patient's queue token status (e.g. IN_PROGRESS, COMPLETED, SKIPPED, CANCELLED).
 */
export async function updateQueueTokenStatus(tokenId, status, notes) {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: null,
      error: 'Authentication token required',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/queue/tokens/${tokenId}/status`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify({ status, notes: notes || '' }),
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Failed to update token status (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Network error updating queue token status',
    };
  }
}

/**
 * Retrieves the status transition audit history for a queue token.
 */
export async function fetchQueueTokenHistory(tokenId) {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: [],
      error: 'Authentication token required',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/queue/tokens/${tokenId}/history`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: [],
        error: data.message || `Failed to fetch queue token history (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: [],
      error: error.message || 'Network error fetching queue token history',
    };
  }
}

// ==============================================================================
// Customer Queue Tracking APIs (Phase 6)
// ==============================================================================

/**
 * Retrieves the customer's active queue token with live people ahead count and estimated wait time.
 */
export async function fetchMyActiveQueueToken(appointmentId) {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: null,
      error: 'Authentication token required',
    };
  }

  try {
    const url = appointmentId
      ? `${API_BASE_URL}/api/queue/my-token?appointmentId=${encodeURIComponent(appointmentId)}`
      : `${API_BASE_URL}/api/queue/my-token`;

    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Failed to fetch queue token (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Network error tracking queue status',
    };
  }
}

/**
 * Retrieves all queue tokens booked by the authenticated customer.
 */
export async function fetchMyQueueTokens() {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: [],
      error: 'Authentication token required',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/queue/my-tokens`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: [],
        error: data.message || `Failed to fetch tokens (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: [],
      error: error.message || 'Network error loading queue tokens',
    };
  }
}

// ==============================================================================
// Administrator Dashboard APIs (Phase 6)
// ==============================================================================

/**
 * Retrieves operational counts and summary metrics for clinic administrators.
 */
export async function fetchAdminStats() {
  const token = getStoredToken();
  if (!token) {
    return {
      success: false,
      data: null,
      error: 'Authentication token required',
    };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/admin/stats`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();

    if (!response.ok) {
      return {
        success: false,
        data: null,
        error: data.message || `Failed to fetch admin stats (HTTP ${response.status})`,
      };
    }

    return {
      success: true,
      data: data,
      error: null,
    };
  } catch (error) {
    return {
      success: false,
      data: null,
      error: error.message || 'Network error loading administrator stats',
    };
  }
}

/**
 * Retrieves list of all registered user accounts (admin only).
 */
export async function fetchAdminUsers() {
  const token = getStoredToken();
  if (!token) {
    return { success: false, data: [], error: 'Authentication token required' };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/admin/users`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();
    if (!response.ok) {
      return { success: false, data: [], error: data.message || `HTTP ${response.status}` };
    }
    return { success: true, data: data, error: null };
  } catch (error) {
    return { success: false, data: [], error: error.message || 'Network error' };
  }
}

/**
 * Retrieves list of all provider profiles (admin only).
 */
export async function fetchAdminProviders() {
  const token = getStoredToken();
  if (!token) {
    return { success: false, data: [], error: 'Authentication token required' };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/admin/providers`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();
    if (!response.ok) {
      return { success: false, data: [], error: data.message || `HTTP ${response.status}` };
    }
    return { success: true, data: data, error: null };
  } catch (error) {
    return { success: false, data: [], error: error.message || 'Network error' };
  }
}

/**
 * Retrieves list of all appointments across the clinic (admin only).
 */
export async function fetchAdminAppointments() {
  const token = getStoredToken();
  if (!token) {
    return { success: false, data: [], error: 'Authentication token required' };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/admin/appointments`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();
    if (!response.ok) {
      return { success: false, data: [], error: data.message || `HTTP ${response.status}` };
    }
    return { success: true, data: data, error: null };
  } catch (error) {
    return { success: false, data: [], error: error.message || 'Network error' };
  }
}

/**
 * Retrieves all queue token activity across providers (admin only).
 */
export async function fetchAdminQueue() {
  const token = getStoredToken();
  if (!token) {
    return { success: false, data: [], error: 'Authentication token required' };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/admin/queue`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();
    if (!response.ok) {
      return { success: false, data: [], error: data.message || `HTTP ${response.status}` };
    }
    return { success: true, data: data, error: null };
  } catch (error) {
    return { success: false, data: [], error: error.message || 'Network error' };
  }
}

/**
 * Retrieves daily and monthly analytics, queue trends, and average wait time (admin only).
 */
export async function fetchAdminAnalytics(startDate, endDate) {
  const token = getStoredToken();
  if (!token) {
    return { success: false, data: null, error: 'Authentication token required' };
  }

  try {
    const params = new URLSearchParams();
    if (startDate) params.append('startDate', startDate);
    if (endDate) params.append('endDate', endDate);
    const queryString = params.toString() ? `?${params.toString()}` : '';

    const response = await fetch(`${API_BASE_URL}/api/admin/analytics${queryString}`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
    });

    const data = await response.json();
    if (!response.ok) {
      return { success: false, data: null, error: data.message || `HTTP ${response.status}` };
    }
    return { success: true, data: data, error: null };
  } catch (error) {
    return { success: false, data: null, error: error.message || 'Network error fetching analytics' };
  }
}

/**
 * Updates a user's system role (admin only).
 */
export async function updateAdminUserRole(userId, newRole) {
  const token = getStoredToken();
  if (!token) {
    return { success: false, data: null, error: 'Authentication token required' };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}/role`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: JSON.stringify({ role: newRole }),
    });

    const data = await response.json();
    if (!response.ok) {
      return { success: false, data: null, error: data.message || `HTTP ${response.status}` };
    }
    return { success: true, data: data, error: null };
  } catch (error) {
    return { success: false, data: null, error: error.message || 'Network error updating user role' };
  }
}

/**
 * Toggles a provider's active status (admin only).
 */
export async function toggleAdminProviderActive(providerId) {
  const token = getStoredToken();
  if (!token) {
    return { success: false, data: null, error: 'Authentication token required' };
  }

  try {
    const response = await fetch(`${API_BASE_URL}/api/admin/providers/${providerId}/toggle-active`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
    });

    const data = await response.json();
    if (!response.ok) {
      return { success: false, data: null, error: data.message || `HTTP ${response.status}` };
    }
    return { success: true, data: data, error: null };
  } catch (error) {
    return { success: false, data: null, error: error.message || 'Network error toggling provider status' };
  }
}
