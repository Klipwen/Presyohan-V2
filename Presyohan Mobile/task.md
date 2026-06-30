# Styling Consistency & Navigation Transitions Task Checklist

- [x] XML Layout Consistency & Positioning
  - [x] Modify `activity_signup.xml` to remove the redundant back button.
  - [x] Rewrite `activity_verify_email.xml` to match Signup/Login structure (logo, bottom verify button, horizontal resend layout, and circular back button).
  - [x] Update `activity_forgot_password.xml` circular back button to use `bg_nav_icon_active`.
  - [x] Rewrite `activity_enter_reset_code.xml` circular back button background to `bg_nav_icon_active`, set correct title "Enter Verification Code", and place button at parent bottom.
  - [x] Update `activity_reset_password.xml` circular back button to use `bg_nav_icon_active`.
  - [x] Update `activity_onboarding.xml` back button ID and background, swap stepper to sit below navigation buttons.

- [x] Visual Spacing Alignment & Back Button Consistency
  - [x] Aligned the vertical constraints and bias of `activity_forgot_password.xml`, `activity_verify_email.xml`, `activity_enter_reset_code.xml`, and `activity_reset_password.xml` content containers to match `activity_login.xml` (`app:layout_constraintBottom_toBottomOf="parent"` with bias `0.3`).
  - [x] Updated all circular back buttons to have dimensions of `40dp` x `40dp` with padding `8dp` and matching layout constraints.
  - [x] Override transit animations to instant for SignUp and ForgotPassword transitions inside `LoginActivity.kt`.

- [x] Onboarding Card Dynamic Color Management
  - [x] Updated onboarding layout with unique subview IDs inside cards.
  - [x] Created `styleCard` helper in `OnboardingActivity.kt`.
  - [x] Programmed selection styles to turn outline, texts, and icons fully orange when highlighted, and back to teal/dark blue when unselected.

- [x] OTP Layout Refinements & Toast Feedback
  - [x] Split the email address to a separate line in verification screens, styled in bold orange.
  - [x] Moved the Resend Code layout directly above the "Verify Code" button.
  - [x] Removed all automatic code resending on invalid token callbacks.
  - [x] Replaced error messages with a Toast saying "Invalid or expired code" upon validation failure.

- [x] Onboarding Success Screen Design (Step 3)
  - [x] Integrated horizontal dashed separator line layout with software layer drawing.
  - [x] Implemented layered profile picture avatar layout with shadow offset.
  - [x] Split greetings into quote next to avatar and body texts (Tindero vs Customer paths) styled in bold orange.
  - [x] Added right-aligned CEO signature layout.

- [x] Card Styling & Dynamic Animations
  - [x] Set all unselected card descriptions to print in teal color to match design illustrations.
  - [x] Implemented staggered slide-up and fade-in entrance transitions for all onboarding cards and step panels.
  - [x] Added a responsive spring bounce selection animation that triggers when tapping cards.

- [x] Bouncing Dots Transitions & Stores Redirection
  - [x] Added centered loadingDotsContainer inside the onboarding layout frame.
  - [x] Created `startDotsAnimation` and `stopDotsAnimation` to animate 3 loading dots in a wave.
  - [x] Intercepted `NEXT` and `LAUNCH APP` clicks with `performDelayedNextTransition` (delays steps by `1.2s` with animated dots active).
  - [x] Swapped positions of Title and Icons inside Suki Yes/No Step 2 Customer cards to follow the design exactly.
  - [x] Set unchosen cards to scale down and fade out when navigating next.
  - [x] Programmed backing out of steps to reset choices and disable the `NEXT` button until selected again.
  - [x] Reverted the device-wide shared preference onboarding check from SplashActivity and LoginActivity to prevent new logins from bypassing onboarding.
  - [x] Changed `launchMainApp()` to await `setOnboardingCompleted()` network task on `lifecycleScope` before starting intents and finishing.
  - [x] Kept standard `FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP` flags for launching Customer Home from Store View and Select Presyohan activities, ensuring redirection to the Stores tab.
