import React, { useRef } from 'react'

export default function FeaturesSection({ feature1Cards, feature2Cards, feature3Cards }) {
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

  return (
    <section id="features" className="lp-features lp-section">
      <div className="lp-container">
        <h2 className="lp-section-title">Comprehensive Platform Features</h2>
        <p className="lp-section-text lp-section-text-center">
          Presyohan is more than just a price list—it's a comprehensive platform designed to give Store Owners and Managers complete control over pricing, inventory data, and staff access.
        </p>

        <div className="lp-feature-group">
          <div className="lp-feature-head">
            <div className="lp-icon-box orange">📦</div>
            <h3 className="lp-feature-title">Unified Product &amp; Category Management</h3>
          </div>
          <p className="lp-section-text">Manage your entire product catalog efficiently with robust Create, Read, Update, and Delete (CRUD) functionality.</p>
          <div className="lp-grid cards-3">
            {feature1Cards.map((item, index) => (
              <div key={index} className="lp-mini-card" onClick={handleCardClick}>
                <div className="lp-mini-icon">{item.icon}</div>
                <h4 className="lp-mini-title">{item.title}</h4>
                <p className="lp-mini-text">{item.description}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="lp-feature-group">
          <div className="lp-feature-head">
            <div className="lp-icon-box teal">👥</div>
            <h3 className="lp-feature-title">Team &amp; Store Control</h3>
          </div>
          <p className="lp-section-text">Maintain security and accountability with precise, role-based controls over who can access and modify store data.</p>
          <div className="lp-grid cards-4">
            {feature2Cards.map((item, index) => (
              <div key={index} className="lp-mini-card" onClick={handleCardClick}>
                <div className="lp-mini-icon">{item.icon}</div>
                <h4 className="lp-mini-title">{item.title}</h4>
                <p className="lp-mini-text">{item.description}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="lp-feature-group">
          <div className="lp-feature-head">
            <div className="lp-icon-box green">📊</div>
            <h3 className="lp-feature-title">Data Operations &amp; Auditing</h3>
          </div>
          <p className="lp-section-text">Move beyond manual data entry with powerful bulk tools and clear audit trails.</p>
          <div className="lp-grid cards-4">
            {feature3Cards.map((item, index) => (
              <div key={index} className="lp-mini-card" onClick={handleCardClick}>
                <div className="lp-mini-icon">{item.icon}</div>
                <h4 className="lp-mini-title">{item.title}</h4>
                <p className="lp-mini-text">{item.description}</p>
              </div>
            ))}
          </div>
        </div>

        
      </div>
    </section>
  )
}
