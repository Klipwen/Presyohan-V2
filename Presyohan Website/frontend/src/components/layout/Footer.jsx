import React from 'react';
import { Link } from 'react-router-dom';

export default function Footer() {
  return (
    <footer>
      <div className="footer-content">
        <div className="footer-section">
          <h3>About Presyohan</h3>
          <p>
            Presyohan is your centralized, role-based pricing hub. Keep your team aligned with a single source of truth for product prices across web and mobile — faster, clearer, and always up to date.
          </p>
          <Link to="/#about">More</Link>
        </div>
        <div className="footer-section">
          <h3>Get the App</h3>
          <p>Check the correct price instantly anywhere on the floor.</p>
          <Link to="/#download">Go to Download Section</Link>
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
          <p>Email: presyohan@gmail.com</p>
          <p>Phone: +63 932 430 8387</p>
          <p>Location: Curva Medellin, Cebu City, Philippines</p>
        </div>
      </div>
      <div className="footer-bottom">
        <p>&copy; 2025 Presyohan. All rights reserved.</p>
      </div>
    </footer>
  );
}