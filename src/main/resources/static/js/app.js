// ============================================
// GOODFELLAZ17 - Main App Logic
// Client-side routing + Panel interactions
// No backend calls in v1
// ============================================

// ===== VIEW MANAGEMENT =====
const VIEWS = {
    landing: null,
    panel: null,
    docs: null
};

// Initialize on DOM load
document.addEventListener('DOMContentLoaded', () => {
    // Cache view elements
    VIEWS.landing = document.getElementById('view-landing');
    VIEWS.panel = document.getElementById('view-panel');
    VIEWS.docs = document.getElementById('view-docs');
    
    // Initialize Panel
    initializePanel();
    
    // Handle initial route from hash
    const initialView = location.hash.replace('#', '') || 'landing';
    navigateTo(initialView);
    
    // Handle browser back/forward
    window.addEventListener('popstate', () => {
        const view = location.hash.replace('#', '') || 'landing';
        navigateTo(view, false);
    });
});

/**
 * Navigate to a specific view
 * @param {string} viewName - 'landing', 'panel', or 'docs'
 * @param {boolean} updateHistory - Whether to push to history
 */
function navigateTo(viewName, updateHistory = true) {
    // Validate view exists
    if (!VIEWS[viewName]) {
        viewName = 'landing';
    }
    
    // Hide all views
    Object.values(VIEWS).forEach(view => {
        if (view) view.classList.add('hidden');
    });
    
    // Show target view
    VIEWS[viewName].classList.remove('hidden');
    
    // Update URL hash
    if (updateHistory) {
        history.pushState(null, '', `#${viewName}`);
    }
    
    // Scroll to top
    window.scrollTo(0, 0);
    
    // Log navigation (for debugging)
    console.log(`[GF17] Navigated to: ${viewName}`);
}

// ===== PANEL DATA =====
// Human-readable service data (no backend IDs or prices)
const SERVICE_CATEGORIES = [
    {
        id: 'plays',
        name: 'Plays',
        icon: '▶️',
        options: [
            { id: 'plays-ww', name: 'Worldwide Plays', desc: 'Global reach, natural drip pattern', tier: 'basic' },
            { id: 'plays-usa', name: 'USA Plays', desc: 'Premium geo-targeted, higher conversion', tier: 'premium' },
            { id: 'plays-chart', name: 'Chart Push', desc: 'Elite algorithm targeting for viral potential', tier: 'elite' },
            { id: 'plays-drip', name: 'Drip Feed', desc: '24-48h gradual delivery, undetectable', tier: 'safe' }
        ]
    },
    {
        id: 'monthly',
        name: 'Monthly',
        icon: '👥',
        options: [
            { id: 'monthly-global', name: 'Global Listeners', desc: 'Worldwide monthly listener boost', tier: 'basic' },
            { id: 'monthly-usa', name: 'USA Listeners', desc: 'Premium US-based listener profiles', tier: 'premium' },
            { id: 'monthly-drip', name: 'Monthly Drip', desc: '30-day sustained listener growth', tier: 'elite' }
        ]
    },
    {
        id: 'engagement',
        name: 'Engage',
        icon: '💾',
        options: [
            { id: 'engage-saves', name: 'Saves', desc: 'Library saves from active accounts', tier: 'basic' },
            { id: 'engage-follows', name: 'Followers', desc: 'Artist profile followers', tier: 'premium' },
            { id: 'engage-combo', name: 'Full Engagement', desc: 'Saves + Follows + Playlist adds combo', tier: 'elite' }
        ]
    },
    {
        id: 'playlists',
        name: 'Playlists',
        icon: '🎵',
        options: [
            { id: 'playlist-follows', name: 'Playlist Followers', desc: 'Grow your playlist audience', tier: 'basic' },
            { id: 'playlist-place', name: 'Playlist Placement', desc: 'Get added to curated playlists', tier: 'premium' },
            { id: 'playlist-editorial', name: 'Editorial Push', desc: 'Target editorial playlist consideration', tier: 'elite' }
        ]
    }
];

// Panel state
let selectedCategory = null;
let selectedOption = null;

// ===== PANEL INITIALIZATION =====
function initializePanel() {
    renderCategories();
}

/**
 * Render category buttons in sidebar
 */
function renderCategories() {
    const container = document.getElementById('category-list');
    if (!container) return;
    
    container.innerHTML = SERVICE_CATEGORIES.map(cat => `
        <button class="category-btn" data-category="${cat.id}" onclick="selectCategory('${cat.id}')">
            <span class="cat-icon">${cat.icon}</span> ${cat.name}
        </button>
    `).join('');
}

/**
 * Select a category and show its options
 * @param {string} categoryId
 */
function selectCategory(categoryId) {
    const category = SERVICE_CATEGORIES.find(c => c.id === categoryId);
    if (!category) return;
    
    selectedCategory = category;
    selectedOption = null;
    
    // Update active state on buttons
    document.querySelectorAll('.category-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.category === categoryId);
    });
    
    // Render options
    renderOptions(category.options);
    
    // Update impact preview
    updateImpactPreview();
}

/**
 * Render service options for selected category
 * @param {Array} options
 */
function renderOptions(options) {
    const container = document.getElementById('options-list');
    if (!container) return;
    
    container.innerHTML = options.map(opt => `
        <div class="option-card" data-option="${opt.id}" onclick="selectOption('${opt.id}')">
            <div class="option-info">
                <div class="option-name">${opt.name}</div>
                <div class="option-desc">${opt.desc}</div>
            </div>
            <span class="badge badge-${opt.tier}">${opt.tier.toUpperCase()}</span>
        </div>
    `).join('');
}

/**
 * Select a specific service option
 * @param {string} optionId
 */
function selectOption(optionId) {
    if (!selectedCategory) return;
    
    const option = selectedCategory.options.find(o => o.id === optionId);
    if (!option) return;
    
    selectedOption = option;
    
    // Update active state
    document.querySelectorAll('.option-card').forEach(card => {
        card.classList.toggle('selected', card.dataset.option === optionId);
    });
    
    // Update impact preview
    updateImpactPreview();
}

/**
 * Update the impact preview text based on selections
 */
function updateImpactPreview() {
    const preview = document.getElementById('impact-preview');
    if (!preview) return;
    
    const amount = document.getElementById('amount-input')?.value || '';
    
    if (!selectedCategory) {
        preview.textContent = '📊 Select a service and enter details to see estimated impact';
        return;
    }
    
    if (!selectedOption) {
        preview.textContent = `📊 Select a ${selectedCategory.name.toLowerCase()} option above`;
        return;
    }
    
    if (!amount || parseInt(amount) < 100) {
        preview.textContent = `📊 Enter an amount (min 100) for ${selectedOption.name}`;
        return;
    }
    
    const amountNum = parseInt(amount).toLocaleString();
    const tierText = selectedOption.tier === 'elite' ? 'premium' : 
                     selectedOption.tier === 'safe' ? 'gradual' : 'standard';
    
    preview.innerHTML = `📊 <strong>${amountNum}</strong> ${selectedCategory.name.toLowerCase()} will be delivered using <strong>${tierText}</strong> routing over 24-72 hours`;
}

// Listen for amount input changes
document.addEventListener('input', (e) => {
    if (e.target.id === 'amount-input' || e.target.id === 'target-input') {
        updateImpactPreview();
    }
});

// ===== REQUEST HANDLING =====

/**
 * Prepare the request (client-side only, no API call)
 */
function prepareRequest() {
    const targetInput = document.getElementById('target-input');
    const amountInput = document.getElementById('amount-input');
    const previewBox = document.getElementById('preview-box');
    
    // Validation
    if (!selectedCategory) {
        alert('Please select a category first');
        return;
    }
    
    if (!selectedOption) {
        alert('Please select a service option');
        return;
    }
    
    const target = targetInput?.value?.trim() || '';
    const amount = amountInput?.value?.trim() || '';
    
    if (!target) {
        alert('Please enter a target (link, artist, or identifier)');
        targetInput?.focus();
        return;
    }
    
    if (!amount || parseInt(amount) < 100) {
        alert('Please enter a valid amount (minimum 100)');
        amountInput?.focus();
        return;
    }
    
    // Build request object (human-readable, no backend coupling)
    const request = {
        categoryLabel: selectedCategory.name,
        optionLabel: selectedOption.name,
        optionTier: selectedOption.tier,
        targetText: target,
        amountText: parseInt(amount).toLocaleString()
    };
    
    // Render preview
    if (previewBox) {
        previewBox.innerHTML = `
            <div class="preview-line">
                <span class="preview-key">Category:</span>
                <span class="preview-value">${request.categoryLabel}</span>
            </div>
            <div class="preview-line">
                <span class="preview-key">Option:</span>
                <span class="preview-value">${request.optionLabel} [${request.optionTier.toUpperCase()}]</span>
            </div>
            <div class="preview-line">
                <span class="preview-key">Target:</span>
                <span class="preview-value">${escapeHtml(request.targetText)}</span>
            </div>
            <div class="preview-line">
                <span class="preview-key">Amount:</span>
                <span class="preview-value">${request.amountText}</span>
            </div>
        `;
    }
    
    // Log for debugging
    console.log('[GF17] Request prepared:', request);
    
    // Scroll to preview
    document.getElementById('preview-section')?.scrollIntoView({ behavior: 'smooth' });
}

/**
 * Reset the panel to initial state
 */
function resetPanel() {
    // Clear selections
    selectedCategory = null;
    selectedOption = null;
    
    // Clear UI
    document.querySelectorAll('.category-btn').forEach(btn => btn.classList.remove('active'));
    
    const optionsList = document.getElementById('options-list');
    if (optionsList) {
        optionsList.innerHTML = '<p class="options-placeholder">← Select a category to see options</p>';
    }
    
    // Clear inputs
    const targetInput = document.getElementById('target-input');
    const amountInput = document.getElementById('amount-input');
    if (targetInput) targetInput.value = '';
    if (amountInput) amountInput.value = '';
    
    // Reset previews
    updateImpactPreview();
    
    const previewBox = document.getElementById('preview-box');
    if (previewBox) {
        previewBox.innerHTML = '<p class="preview-empty">Your prepared request will appear here</p>';
    }
    
    console.log('[GF17] Panel reset');
}

// ===== UTILITIES =====

/**
 * Escape HTML to prevent XSS
 * @param {string} text
 * @returns {string}
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Check if app is offline
 * @returns {boolean}
 */
function isOffline() {
    return !navigator.onLine;
}

// Handle online/offline status
window.addEventListener('online', () => {
    console.log('[GF17] Back online');
    document.body.classList.remove('offline');
});

window.addEventListener('offline', () => {
    console.log('[GF17] Gone offline');
    document.body.classList.add('offline');
});

// ===== DEBUG =====
console.log('[GF17] App initialized - Beşiktaş Ultra × Spotify Engine');
