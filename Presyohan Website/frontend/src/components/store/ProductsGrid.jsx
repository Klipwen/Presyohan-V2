import React from 'react';
import ItemCard from '../items/ItemCard';

export default function ProductsGrid({ products, categories = [], onEditItem, onDeleteItem, onAddCategory }) {
  return (
    <div style={{ flex: 1, padding: '30px', background: '#f5f5f5' }}>
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
        gap: '20px'
      }}>
        {products.map((product) => (
          <ItemCard
            key={product.id}
            item={product}
            categories={categories}
            onEdit={onEditItem}
            onDelete={onDeleteItem}
            onAddCategory={onAddCategory}
          />
        ))}
      </div>
    </div>
  );
}