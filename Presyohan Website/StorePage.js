/**
 * Toggles the visibility of the side menu and the overlay.
 */
function toggleSideMenu() {
    const sideMenu = document.getElementById('sideMenu');
    const overlay = document.getElementById('sideMenuOverlay');
    
    sideMenu.classList.toggle('active');
    overlay.classList.toggle('active');
}

/**
 * Opens the "Add Store" modal.
 */
function openModal() {
    const modal = document.getElementById('addStoreModal');
    if (modal) {
        modal.classList.add('active');
    }
}

/**
 * Closes the "Add Store" modal.
 */
function closeModal() {
    const modal = document.getElementById('addStoreModal');
    if (modal) {
        modal.classList.remove('active');
    }
}

/**
 * Placeholder function for "Join Store" logic.
 */
function joinStore() {
    alert('Join Store functionality will be implemented here');
    closeModal();
}

/**
 * Placeholder function for "Create Store" logic.
 */
function createStore() {
    alert('Create Store functionality will be implemented here');
    closeModal();
}

// Add event listener to the modal overlay to close it when clicked
const addStoreModal = document.getElementById('addStoreModal');
if (addStoreModal) {
    addStoreModal.addEventListener('click', function(e) {
        // Close the modal only if the click is on the modal background itself (not the content)
        if (e.target === this) {
            closeModal();
        }
    });
}
