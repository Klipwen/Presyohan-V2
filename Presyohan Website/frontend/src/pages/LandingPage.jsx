import React, { useEffect, useState } from 'react';
import '../styles/LandingPage.css';
import AuthHeader from '../components/layout/AuthHeader';
import Footer from '../components/layout/Footer';
import { useNavigate, useLocation } from 'react-router-dom';
import { supabase } from '../config/supabaseClient';
import HeroSection from '../components/landing/HeroSection';
import AboutSection from '../components/landing/AboutSection';
import FeaturesSection from '../components/landing/FeaturesSection';
import DownloadSection from '../components/landing/DownloadSection';

export default function LandingPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [apkUrl, setApkUrl] = useState(import.meta.env.VITE_DOWNLOAD_APK_URL || '');

  

  useEffect(() => {
    if (location.hash) {
      const id = location.hash.replace('#', '');
      const el = document.getElementById(id);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }
  }, [location]);

  useEffect(() => {
    if (!apkUrl) {
      const { data } = supabase.storage.from('presyohan.apk').getPublicUrl('presyohan.apk');
      if (data?.publicUrl) setApkUrl(data.publicUrl);
    }
  }, [apkUrl]);

  const comparisonRows = [
    {
      feature: 'Price Updates',
      old: 'Print new sheets, text managers, hope everyone remembers.',
      new: 'Instant Updates: Owners and Managers can update prices in seconds, and the changes appear instantly for all members.'
    },
    {
      feature: 'New Staff Onboarding',
      old: 'Weeks of training on price changes and product locations.',
      new: 'Zero Training Required: New staff instantly see the current, official price list and product information.'
    },
    {
      feature: 'Error Reduction',
      old: 'Human error from misreading a list or forgetting a price change.',
      new: 'Zero Price Confusion: A single, consistent source of truth eliminates arguments and customer disputes.'
    },
    {
      feature: 'Multi-Store Management',
      old: 'Manual spreadsheets and different lists for every branch.',
      new: 'Copy & Scale: Effortlessly copy entire price lists, promotions, and categories across your store branches to maintain standards.'
    },
    {
      feature: 'Bulk Import & Export',
      old: 'Manual updates across spreadsheets; copy-paste errors and rework.',
      new: 'Excel/CSV import with flexible mapping and clean exports for printing — fast and reliable.'
    },
    {
      feature: 'Member Access & Accountability',
      old: 'Shared accounts and uncontrolled edits with no clear audit trail.',
      new: 'Role-based permissions (Owner/Manager/Sale Staff) with audit timestamps to track every change.'
    }
  ];

  const roles = [
    {
      icon: '👑',
      title: 'Store Owners',
      subtitle: 'Full Control',
      description: 'Manage members, handle bulk imports/exports, and access all settings.'
    },
    {
      icon: '⚙️',
      title: 'Managers',
      subtitle: 'Day-to-Day Operations',
      description: 'Create, update, and delete products and categories. Keep the store running smoothly.'
    },
    {
      icon: '👁️',
      title: 'Sale Staff',
      subtitle: 'View-Only Access',
      description: 'Securely view the most current prices and product details on the fly.'
    }
  ];

  const feature1Cards = [
    {
      icon: '✏️',
      title: 'Product & Item CRUD',
      description:
        'Easily create, edit, and update product details, including name, price, unit, and description. Every change is instantly visible to all store members.'
    },
    {
      icon: '🗂️',
      title: 'Category Management',
      description:
        'Organize your products with flexible categories. Deleting a category automatically handles associated products to maintain a clean and consistent database.'
    },
    {
      icon: '🔍',
      title: 'Advanced Search & Filter',
      description:
        'Quickly find any product by name, category, or unit. Use sorting and filtering by price range or last updated time to manage large catalogs with ease.'
    }
  ];

  const feature2Cards = [
    {
      icon: '🔐',
      title: 'Role-Based Access',
      description:
        'Enforce strict permissions: Owners have full control, Managers can edit products, and Sale Staff have secure, view-only access.'
    },
    {
      icon: '✉️',
      title: 'Easy Member Invitation',
      description:
        'Invite new Managers or Sale Staff by sending an email invitation or generating a secure, expiring invite code they can redeem to join your store.'
    },
    {
      icon: '🔄',
      title: 'Ownership Transfer',
      description:
        "Securely transfer the Owner role to another Manager when needed, ensuring the store's control remains protected and accountable."
    },
    {
      icon: '🏪',
      title: 'Store Management',
      description:
        'Create new stores and manage settings like name and branch details. Owners can delete a store only after ensuring another owner exists.'
    }
  ];

  const feature3Cards = [
    {
      icon: '📥',
      title: 'Bulk Import from Excel/CSV',
      description:
        'Import hundreds of products instantly. Use our mapping UI to link your spreadsheet columns to database fields, preview changes, and manage conflicts (overwrite or skip).'
    },
    {
      icon: '🖨️',
      title: 'Price List Export & Print',
      description:
        'Generate and export your entire price catalog to an Excel (.xlsx) file, perfect for printing hard copies or internal record-keeping.'
    },
    {
      icon: '📋',
      title: 'Copy Prices Between Stores',
      description:
        'Standardize pricing by copying an entire price list from one branch to another, complete with a conflict resolution preview.'
    },
    {
      icon: '🔔',
      title: 'Activity Notifications',
      description:
        'Stay informed with a simple notification system for important events like new member invitations, role changes, and large data imports.'
    }
  ];

  return (
    <div className="lp-root">
      <AuthHeader />
      <HeroSection onGetStarted={() => navigate('/login')} />
      <AboutSection comparisonRows={comparisonRows} roles={roles} />
      <FeaturesSection feature1Cards={feature1Cards} feature2Cards={feature2Cards} feature3Cards={feature3Cards} />
      <DownloadSection apkUrl={apkUrl} />
      <Footer />
    </div>
  );
}
