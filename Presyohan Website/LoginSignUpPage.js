/**
 * Switches between the Login and Sign Up tabs and forms.
 * @param {string} tab - The tab to switch to ('login' or 'signup').
 */
function switchTab(tab) {
    const tabs = document.querySelectorAll('.tab');
    const forms = document.querySelectorAll('.form-container');
    
    // Deactivate all tabs and hide all forms
    tabs.forEach(t => t.classList.remove('active'));
    forms.forEach(f => f.classList.remove('active'));
    
    // Activate the selected tab and show the corresponding form
    if (tab === 'login') {
        // Find the login tab and form
        const loginTab = Array.from(tabs).find(t => t.textContent.toLowerCase() === 'login');
        if (loginTab) loginTab.classList.add('active');
        document.getElementById('loginForm').classList.add('active');
    } else if (tab === 'signup') {
        // Find the sign up tab and form
        const signupTab = Array.from(tabs).find(t => t.textContent.toLowerCase() === 'sign up');
        if (signupTab) signupTab.classList.add('active');
        document.getElementById('signupForm').classList.add('active');
    }
}

/**
 * Toggles the visibility of a password input field.
 * @param {string} inputId - The ID of the password input field.
 */
function togglePassword(inputId) {
    const input = document.getElementById(inputId);
    const button = input.nextElementSibling; // The button is the next sibling element (the eye icon)
    
    if (input.type === 'password') {
        input.type = 'text';
        button.textContent = '🙈'; // Icon for 'hide password'
    } else {
        input.type = 'password';
        button.textContent = '👁️'; // Icon for 'show password'
    }
}

/**
 * Handles the login form submission. (Placeholder)
 * @param {Event} e - The form submission event.
 */
function handleLogin(e) {
    e.preventDefault();
    // ALERT: In a real application, you'd perform AJAX/Fetch request for authentication.
    alert('Login functionality would be implemented here');
}

/**
 * Handles the sign up form submission, including password matching validation. (Placeholder)
 * @param {Event} e - The form submission event.
 */
function handleSignup(e) {
    e.preventDefault();
    const password = document.getElementById('signupPassword').value;
    const confirmPassword = document.getElementById('confirmPassword').value;
    
    if (password !== confirmPassword) {
        alert('Passwords do not match! Please make sure both passwords are the same.');
        return;
    }
    
    // ALERT: In a real application, you'd perform AJAX/Fetch request for user registration.
    alert('Sign up functionality would be implemented here');
}

/**
 * Handles social login button clicks. (Placeholder)
 * @param {string} provider - The social media provider name (e.g., 'Google', 'Facebook').
 */
function handleSocialLogin(provider) {
    // ALERT: In a real application, this would initiate an OAuth flow.
    alert(`${provider} login would be implemented here`);
}