import React from 'react';
import { Link } from 'react-router-dom';

export default function Footer() {
  return (
    <footer>
      <div className="footer-content">
        <div className="footer-section">
          <h3>About Presyohan</h3>
          <p>Your trusted platform for finding the best prices in one place. Compare, save, and shop smarter.</p>
        </div>
        <div className="footer-section">
          <h3>Quick Links</h3>
          <Link to="/login">Login</Link>
          <Link to="/store">Store</Link>
          <Link to="/verify-email">Verify Email</Link>
          <a href="#">Contact</a>
        </div>
        <div className="footer-section">
          <h3>Support</h3>
          <a href="#">Help Center</a>
          <a href="#">Privacy Policy</a>
          <a href="#">Terms of Service</a>
          <a href="#">FAQ</a>
        </div>
        <div className="footer-section">
          <h3>Contact Us</h3>
          <p>Email: support@presyohan.com</p>
          <p>Phone: +63 123 456 7890</p>
          <p>Location: Cebu City, Philippines</p>
        </div>
      </div>
      <div className="footer-bottom">
        <p>&copy; 2024 Presyohan. All rights reserved.</p>
      </div>
    </footer>
  );
}