import React, { useEffect, useLayoutEffect, useRef, useState } from 'react'

export default function AboutSection({ comparisonRows, roles }) {
  const [slidesPerView, setSlidesPerView] = useState(1)
  const [currentSlide, setCurrentSlide] = useState(0)
  const viewportRef = useRef(null)
  const [viewportWidth, setViewportWidth] = useState(0)

  const glowTimeoutsRef = useRef(new Map())
  const triggerGlow = (el) => {
    if (!el) return
    el.classList.add('glow-click')
    const prev = glowTimeoutsRef.current.get(el)
    if (prev) clearTimeout(prev)
    const tid = setTimeout(() => {
      el.classList.remove('glow-click')
      glowTimeoutsRef.current.delete(el)
    }, 600)
    glowTimeoutsRef.current.set(el, tid)
  }
  const handleCardClick = (e) => {
    triggerGlow(e.currentTarget)
  }

  useEffect(() => {
    const updateSPV = () => {
      const w = window.innerWidth
      if (w >= 1200) setSlidesPerView(3)
      else if (w >= 768) setSlidesPerView(2)
      else setSlidesPerView(1)
    }
    updateSPV()
    window.addEventListener('resize', updateSPV)
    return () => window.removeEventListener('resize', updateSPV)
  }, [])

  useLayoutEffect(() => {
    const readWidth = () => {
      if (viewportRef.current) setViewportWidth(viewportRef.current.offsetWidth)
    }
    readWidth()
    window.addEventListener('resize', readWidth)
    return () => window.removeEventListener('resize', readWidth)
  }, [])

  const totalSlides = comparisonRows.length
  const pageCount = Math.max(1, totalSlides - slidesPerView + 1)
  const prevSlide = () => setCurrentSlide((i) => Math.max(0, i - 1))
  const nextSlide = () => setCurrentSlide((i) => Math.min(pageCount - 1, i + 1))

  return (
    <section id="about" className="lp-section lp-about">
      <div className="lp-container">
        <h2 className="lp-section-title">About Presyohan: Your Smart Pricing Command Center</h2>
        <h3 className="lp-section-subtitle">Stop the Price Guessing Game. Start Selling Smarter.</h3>
        <p className="lp-section-text">
          Presyohan is a centralized, role-based platform designed to eliminate pricing errors and confusion in your store. Think of it as the single, authoritative source of truth for every product price, available instantly to everyone on your team—on both web and mobile. We take the stress out of store operations so you and your team can focus on serving your customers.
        </p>

        <h3 className="lp-block-title">How Presyohan Transforms Your Store:</h3>

        <div className="lp-carousel">
          <button
            className={`lp-carousel-arrow left ${currentSlide === 0 ? 'disabled' : ''}`}
            onClick={prevSlide}
            aria-label="Previous"
          >
            <svg viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>
          </button>
          <div className="lp-carousel-viewport" ref={viewportRef}>
            <div
              className="lp-carousel-track"
              style={{
                transform: (() => {
                  const GAP_PX = 16
                  const gapPercent = viewportWidth > 0 ? (GAP_PX * 100) / viewportWidth : 0
                  const perSlidePercent = 100 / slidesPerView + gapPercent
                  return `translateX(-${currentSlide * perSlidePercent}%)`
                })(),
                transition: 'transform 320ms ease'
              }}
            >
              {comparisonRows.map((row, index) => (
                <div
                  key={index}
                  className="lp-card slide"
                  style={{ flex: `0 0 ${100 / slidesPerView}%` }}
                  onClick={handleCardClick}
                >
                  <div className="lp-card-content">
                    <h4 className="lp-card-title">{row.feature}</h4>
                    <div className="lp-old">
                      <div className="lp-old-label">The Old Way:</div>
                      <p className="lp-old-text">{row.old}</p>
                    </div>
                    <div className="lp-new">
                      <div className="lp-new-label">The Presyohan Way:</div>
                      <p className="lp-new-text">{row.new}</p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="lp-carousel-dots">
            {Array.from({ length: pageCount }).map((_, i) => (
              <button
                key={i}
                className={`dot ${currentSlide === i ? 'active' : ''}`}
                onClick={() => setCurrentSlide(i)}
                aria-label={`Go to slide ${i + 1}`}
              />
            ))}
          </div>
          <button
            className={`lp-carousel-arrow right ${currentSlide >= pageCount - 1 ? 'disabled' : ''}`}
            onClick={nextSlide}
            aria-label="Next"
          >
            <svg viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6z"/></svg>
          </button>
        </div>

        <h3 className="lp-block-title">Powerful Tools for Every Role</h3>
        <div className="lp-grid">
          {roles.map((role, index) => (
            <div key={index} className="lp-role-card" onClick={handleCardClick}>
              <div className="lp-role-icon">{role.icon}</div>
              <h4 className="lp-role-title">{role.title}</h4>
              <div className="lp-role-subtitle">{role.subtitle}</div>
              <p className="lp-role-text">{role.description}</p>
            </div>
          ))}
        </div>

        <p className="lp-info">Presyohan ensures your data is secure, responsive, and always ready for you.</p>
      </div>
    </section>
  )
}
