import { useState } from 'react';

export default function MenuItemImage({ item }) {
  const [failedUrl, setFailedUrl] = useState('');
  return <div className="menu-item-image">
    {item.imageUrl && failedUrl !== item.imageUrl &&
      <img src={item.imageUrl} alt="" loading="lazy" onError={() => setFailedUrl(item.imageUrl)} />}
  </div>;
}
