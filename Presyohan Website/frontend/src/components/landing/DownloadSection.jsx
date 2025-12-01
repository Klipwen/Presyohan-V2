import React, { useEffect, useRef } from 'react'

import splashImg from '../../assets/presyohan app sample/Splash.png'
import loginImg from '../../assets/presyohan app sample/Login.png'
import addStoreImg from '../../assets/presyohan app sample/Add Store.png'
import storeItemsImg from '../../assets/presyohan app sample/Store Items.png'
import productsImg from '../../assets/presyohan app sample/Products.png'
import storeMgmtImg from '../../assets/presyohan app sample/Store Management.png'

export default function DownloadSection({ apkUrl }) {
  const collageRef = useRef(null)

  useEffect(() => {
    const el = collageRef.current
    if (!el) return
    let paused = false
    let rafId
    const step = () => {
      if (!paused) {
        if (el.scrollTop + el.clientHeight >= el.scrollHeight) {
          el.scrollTo({ top: 0, behavior: 'smooth' })
        } else {
          el.scrollTop += 0.4
        }
      }
      rafId = requestAnimationFrame(step)
    }
    rafId = requestAnimationFrame(step)
    const onEnter = () => { paused = true }
    const onLeave = () => { paused = false }
    el.addEventListener('mouseenter', onEnter)
    el.addEventListener('mouseleave', onLeave)
    return () => {
      cancelAnimationFrame(rafId)
      el.removeEventListener('mouseenter', onEnter)
      el.removeEventListener('mouseleave', onLeave)
    }
  }, [])

  return (
    <section id="download" className="lp-section lp-download">
      <div className="lp-container">
        <h2 className="lp-section-title">Get the Presyohan Mobile App</h2>
        <h3 className="lp-section-subtitle">Easy and Portable!</h3>
        <p className="lp-section-text lp-section-text-center">
          Say goodbye to hassle! Make it fast and easy to check the correct price right on the floor. No more running around or asking colleagues—get the price instantly and serve your customers better. <strong>Download the app now!</strong>
        </p>

        <div className="lp-grid-2col">
          <div>
            <h4 className="lp-subblock-title">Why the Mobile App?</h4>
            <ul className="lp-list">
              <li>Instant price lookup anywhere in your store</li>
              <li>Always synced with the latest official price list</li>
              <li>Works across branches with role-based access</li>
              <li>Simple, fast, and built for busy teams</li>
            </ul>
            <div className="lp-store-row">
              <div className="lp-download-label">Download the app now!</div>
              <a href={apkUrl} className="lp-download-btn" target="_blank" rel="noopener noreferrer">
                <div>
                  <div className="lp-store-title">presyohan v2.0</div>
                </div>
              </a>
            </div>
          </div>
          <div>
            <div className="lp-app-collage" ref={collageRef}>
              <div className="lp-app-shot"><img src={splashImg} alt="Splash screen" /><div className="lp-app-caption">Splash</div></div>
              <div className="lp-app-shot"><img src={loginImg} alt="Login screen" /><div className="lp-app-caption">Login</div></div>
              <div className="lp-app-shot"><img src={addStoreImg} alt="Add Store" /><div className="lp-app-caption">Add Store</div></div>
              <div className="lp-app-shot"><img src={storeItemsImg} alt="Store Items" /><div className="lp-app-caption">Store Items</div></div>
              <div className="lp-app-shot"><img src={productsImg} alt="Products" /><div className="lp-app-caption">Products</div></div>
              <div className="lp-app-shot"><img src={storeMgmtImg} alt="Store Management" /><div className="lp-app-caption">Store Management</div></div>
            </div>
            <div className="lp-collage-note">Presyohan app previews aligned to brand design.</div>
          </div>
        </div>
      </div>
    </section>
  )
}

