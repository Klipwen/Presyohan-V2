import React, { useState } from 'react';
import AuthHeader from '../components/layout/AuthHeader';
import Footer from '../components/layout/Footer';
import '../styles/LandingPage.css';
import '../styles/ContactPage.css';

export default function ContactPage() {
  const [form, setForm] = useState({ name: '', email: '', subject: '', message: '' });
  const [status, setStatus] = useState(null);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((f) => ({ ...f, [name]: value }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    // Simple validation
    if (!form.name || !form.email || !form.message) {
      setStatus({ type: 'error', msg: 'Please fill in your name, email, and message.' });
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
      setStatus({ type: 'error', msg: 'Please enter a valid email address.' });
      return;
    }
    // Simulate submission
    setTimeout(() => {
      setStatus({ type: 'success', msg: 'Thanks! We received your message and will get back soon.' });
      setForm({ name: '', email: '', subject: '', message: '' });
    }, 300);
  };

  return (
    <div>
      <AuthHeader />

      <section className="lp-section lp-contact">
        <div className="lp-container">
          <h2 className="lp-section-title">Contact Us</h2>
          <h3 className="lp-section-subtitle">We’d love to hear from you</h3>
          <p className="lp-section-text">
            Have questions, feedback, or need support? Reach out using the form below or via the quick contact info. We’ll respond as soon as we can.
          </p>

          <div className="lp-grid-2col">
            {/* Contact Info */}
            <div>
              <div className="lp-contact-box">
                <div className="lp-contact-card" style={{ marginBottom: 16 }}>
                  <span role="img" aria-label="email">✉️</span>
                  <a className="lp-contact-link" href="mailto:presyohan@gmail.com">presyohan@gmail.com</a>
                </div>
                <div className="lp-contact-card" style={{ marginBottom: 16 }}>
                  <span role="img" aria-label="phone">📞</span>
                  <a className="lp-contact-link" href="tel:+639324308387">+63 932 430 8387</a>
                </div>
                <div className="lp-contact-card" style={{ marginBottom: 16 }}>
                  <span role="img" aria-label="location">📍</span>
                  <span>Curva Medellin, Cebu City, Philippines</span>
                </div>
                <p className="lp-info">Business hours: Mon–Fri, 9am–5pm PHT</p>
              </div>
            </div>

            {/* Contact Form */}
            <div>
              <form className="lp-contact-form" onSubmit={handleSubmit} noValidate>
                <div className="lp-form-row">
                  <div className="lp-form-field">
                    <label htmlFor="name">Name</label>
                    <input id="name" name="name" type="text" value={form.name} onChange={handleChange} placeholder="Your name" />
                  </div>
                  <div className="lp-form-field">
                    <label htmlFor="email">Email</label>
                    <input id="email" name="email" type="email" value={form.email} onChange={handleChange} placeholder="you@example.com" />
                  </div>
                </div>
                <div className="lp-form-field">
                  <label htmlFor="subject">Subject</label>
                  <input id="subject" name="subject" type="text" value={form.subject} onChange={handleChange} placeholder="How can we help?" />
                </div>
                <div className="lp-form-field">
                  <label htmlFor="message">Message</label>
                  <textarea id="message" name="message" rows={6} value={form.message} onChange={handleChange} placeholder="Tell us a bit more..." />
                </div>
                <button type="submit" className="lp-btn-primary">Send Message</button>
                {status && (
                  <p className="lp-form-status" data-type={status.type}>{status.msg}</p>
                )}
              </form>
            </div>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}