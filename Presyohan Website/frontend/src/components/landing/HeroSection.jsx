import React from 'react'
import presyohanLogo from '../../assets/presyohan_logo.png'

export default function HeroSection({ onGetStarted }) {
  return (
    <section id="home" className="lp-hero">
      <div className="lp-hero-container">
        <div className="lp-hero-grid">
          <div className="lp-hero-left">
            <div className="lp-logo-card">
              <img src={presyohanLogo} alt="Presyohan Logo" className="lp-card-logo-img" />
              <div className="lp-card-text-group">
                <span className="lp-card-eyebrow">atong</span>
                <h1 className="lp-card-title">
                  <span className="lp-text-orange">presyo</span>
                  <span className="lp-text-teal">han?</span>
                </h1>
              </div>
            </div>
          </div>

          <div className="lp-hero-right">
            <div className="lp-hero-content">
              <p className="lp-main-text">
                We make it fast and easy to manage your store's entire price list, guaranteeing consistency across all staff and locations.
              </p>

              <div className="lp-cta-wrapper">
                <h2 className="lp-cta-label">wala kahibaw sa presyo?</h2>
                <button
                  className="lp-btn-get-started"
                  type="button"
                  onClick={onGetStarted}
                  aria-label="Get Started"
                >
                  Get Started
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <a
        href="#about"
        className="lp-scroll-arrow"
        aria-label="Scroll to About section"
        onClick={(e) => {
          e.preventDefault()
          const el = document.getElementById('about')
          if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
        }}
      >
        <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
          <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2.5" fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </a>
    </section>
  )
}

