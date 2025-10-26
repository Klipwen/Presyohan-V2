/**
 * Automatically moves the focus to the next input field after a single character is entered.
 * @param {HTMLInputElement} current - The current input element (where the character was just entered).
 * @param {string} nextFieldId - The ID of the next input field to focus on.
 */
function moveToNext(current, nextFieldId) {
    // Only proceed if a character was actually entered
    if (current.value.length === 1) {
        const nextField = document.getElementById(nextFieldId);
        if (nextField) {
            nextField.focus();
        }
    }
}

/**
 * Handles the logic after the last code input field is filled.
 * Currently just logs a message, but could trigger auto-verification.
 */
function handleLastInput() {
    const allFilled = Array.from(document.querySelectorAll('.code-input'))
        .every(input => input.value.length === 1);
        
    if (allFilled) {
        console.log('All code fields filled!');
        // Optional: Call verifyCode() automatically here
        // verifyCode(); 
    }
}

/**
 * Gathers the 6-digit code from all inputs and initiates the verification process.
 */
function verifyCode() {
    const code = Array.from(document.querySelectorAll('.code-input'))
        .map(input => input.value)
        .join('');
        
    if (code.length === 6) {
        // In a real application, you'd send this code to your server for validation (AJAX/Fetch)
        alert(`Verifying code: ${code}`);
    } else {
        alert('Please enter the complete 6-digit code');
    }
}

/**
 * Handles the "Resend Code" action.
 */
function resendCode() {
    // In a real application, you'd trigger an API call to resend a new code to the user's email
    alert('A new verification code has been sent to your email!');
    
    // Optional: Clear the input fields after resending
    document.querySelectorAll('.code-input').forEach(input => input.value = '');
    document.getElementById('code1').focus();
}

/**
 * Adds an event listener to all code inputs to allow moving to the previous field 
 * upon pressing backspace when the current field is empty.
 */
document.querySelectorAll('.code-input').forEach((input, index, inputs) => {
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Backspace' && input.value.length === 0 && index > 0) {
            // Move focus to the previous input field
            inputs[index - 1].focus();
        }
    });
});