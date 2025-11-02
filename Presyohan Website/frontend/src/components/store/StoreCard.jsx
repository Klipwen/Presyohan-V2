import React from 'react';
import storeIcon from '../../assets/icon_store.png';
import '../../styles/StoreCard.css';

// SVG for the three dots (More Options) button
const MoreVerticalIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#666" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="1"></circle>
    <circle cx="12" cy="5" r="1"></circle>
    <circle cx="12" cy="19" r="1"></circle>
  </svg>
);

export default function StoreCard({ name, location, type = 'Type', role, ownerName, imageSrc = storeIcon, href = '#', onOptionsClick }) {
  // Prevent the card link from being triggered when clicking the options button
  const handleOptionsClick = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (onOptionsClick) {
      onOptionsClick();
    }
  };

  return (
    <div className="store-card-wrapper">
      {/* Top-left role indicator and label */}
      {role && (
        <div className="store-indicator" aria-label={role === 'owner' ? 'Your store' : "Other user's store"}>
          <span className={`indicator-dot ${role === 'owner' ? 'owner' : 'member'}`}></span>
          <span className="indicator-label">{role === 'owner' ? 'Your store' : (ownerName ? `${ownerName}'s store` : "Other user's store")}</span>
        </div>
      )}
      <a href={href} className="store-card-link">
        <div className="store-icon">
          <img src={imageSrc} alt={name} />
        </div>
        {/* Name */}
        <div className="store-name-row">
          <h3 className="store-name">{name}</h3>
        </div>
        {/* Store type placeholder */}
        <p className="store-type">{type}</p>
        <p className="store-location">
          <svg viewBox="0 0 24 24">
            <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/>
          </svg>
          {location}
        </p>
      </a>
      <button
        className="store-card-options-btn"
        aria-label={`More options for ${name}`}
        onClick={handleOptionsClick}
      >
        <MoreVerticalIcon />
      </button>
    </div>
  );
}