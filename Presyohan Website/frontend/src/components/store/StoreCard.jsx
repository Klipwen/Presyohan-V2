import React from 'react';
import storeIcon from '../../assets/icon_store.png';

export default function StoreCard({ name, location, imageSrc = storeIcon, href = '#' }) {
  return (
    <a href={href} className="store-card">
      <div className="store-icon">
        <img src={imageSrc} alt={name} />
      </div>
      <h3 className="store-name">{name}</h3>
      <p className="store-location">
        <svg viewBox="0 0 24 24">
          <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/>
        </svg>
        {location}
      </p>
    </a>
  );
}