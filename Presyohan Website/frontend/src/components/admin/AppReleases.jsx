import React, { useState, useEffect } from 'react';
import { supabase } from '../../config/supabaseClient';
import rocketImg from '../../assets/icon_rocket.png';
import launcherImg from '../../assets/icon_presyohan_launcher.png';

export default function AppReleases() {
  const [releases, setReleases] = useState([]);
  const [loading, setLoading] = useState(true);
  
  // Form states
  const [versionCode, setVersionCode] = useState('');
  const [versionName, setVersionName] = useState('');
  const [whatsNew, setWhatsNew] = useState('');
  const [isForced, setIsForced] = useState(false);
  const [apkFile, setApkFile] = useState(null);
  
  const [dragging, setDragging] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [actionLoading, setActionLoading] = useState(null); // 'upload' or releaseId
  const [showPreview, setShowPreview] = useState(false);

  const loadReleases = async () => {
    try {
      setLoading(true);
      const { data, error } = await supabase
        .from('app_releases')
        .select('*')
        .order('version_code', { ascending: false });

      if (error) throw error;
      setReleases(data || []);
    } catch (err) {
      console.error('Failed to load app releases:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReleases();
  }, []);

  const handleDragOver = (e) => {
    e.preventDefault();
    setDragging(true);
  };

  const handleDragLeave = () => {
    setDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    const files = e.dataTransfer.files;
    if (files && files.length > 0) {
      const file = files[0];
      if (file.name.endsWith('.apk')) {
        setApkFile(file);
      } else {
        alert('Invalid file format. Please upload an Android package (.apk) file.');
      }
    }
  };

  const handleFileChange = (e) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      const file = files[0];
      if (file.name.endsWith('.apk')) {
        setApkFile(file);
      } else {
        alert('Invalid file format. Please upload an Android package (.apk) file.');
      }
    }
  };

  const handleSubmitRelease = async (e) => {
    e.preventDefault();
    if (!versionCode || !versionName || !whatsNew.trim() || !apkFile) {
      alert('Please fill out all release metadata and upload an APK file.');
      return;
    }

    // Verify duplicate version code
    const vCodeInt = parseInt(versionCode);
    if (releases.some(r => r.version_code === vCodeInt)) {
      alert(`A release with Version Code ${versionCode} already exists.`);
      return;
    }

    try {
      setActionLoading('upload');
      setUploadProgress(10); // Start progress indicator

      // 1. Upload APK file to Supabase Storage
      const filename = `releases/presyohan-v${vCodeInt}.apk`;
      
      setUploadProgress(30);
      const { data: uploadData, error: uploadErr } = await supabase.storage
        .from('presyohan.apk')
        .upload(filename, apkFile, { 
          cacheControl: '3600',
          upsert: true 
        });

      if (uploadErr) throw uploadErr;

      setUploadProgress(70);

      // 2. Fetch the public download URL
      const { data: urlData } = supabase.storage
        .from('presyohan.apk')
        .getPublicUrl(filename);
      
      const downloadUrl = urlData.publicUrl;

      setUploadProgress(85);

      // 3. Save release metadata in database
      const { data: { user } } = await supabase.auth.getUser();

      const { error: dbErr } = await supabase
        .from('app_releases')
        .insert({
          version_code: vCodeInt,
          version_name: versionName.trim(),
          download_url: downloadUrl,
          whats_new: whatsNew.trim(),
          is_forced: isForced,
          created_by: user?.id || null
        });

      if (dbErr) throw dbErr;

      // Clean up oldest versions to keep only 5 including the new one
      try {
        const { data: currentReleases, error: listErr } = await supabase
          .from('app_releases')
          .select('*')
          .order('version_code', { ascending: true }); // Ascending: oldest first

        if (!listErr && currentReleases && currentReleases.length > 5) {
          const deleteCount = currentReleases.length - 5;
          const oldestToDelete = currentReleases.slice(0, deleteCount);

          for (const oldRelease of oldestToDelete) {
            // Remove APK from Storage
            const oldFilename = `releases/presyohan-v${oldRelease.version_code}.apk`;
            await supabase.storage
              .from('presyohan.apk')
              .remove([oldFilename]);

            // Remove database record
            await supabase
              .from('app_releases')
              .delete()
              .eq('id', oldRelease.id);
          }
        }
      } catch (cleanupErr) {
        console.error('Failed to clean up oldest releases:', cleanupErr);
      }

      setUploadProgress(100);
      
      // Reset form
      setVersionCode('');
      setVersionName('');
      setWhatsNew('');
      setIsForced(false);
      setApkFile(null);
      
      alert('APK Release uploaded and synchronized successfully!');
      await loadReleases();
    } catch (err) {
      console.error('Failed to submit release:', err);
      alert('Error uploading release: ' + err.message);
    } finally {
      setActionLoading(null);
      setUploadProgress(0);
    }
  };

  const handleDeleteRelease = async (release) => {
    if (!window.confirm(`Are you sure you want to delete the release version ${release.version_name} (Code: ${release.version_code})? This will delete both the database record and the uploaded APK file.`)) {
      return;
    }

    try {
      setActionLoading(release.id);

      // 1. Delete file from storage
      const filename = `releases/presyohan-v${release.version_code}.apk`;
      await supabase.storage
        .from('presyohan.apk')
        .remove([filename]);

      // 2. Delete database record
      const { error } = await supabase
        .from('app_releases')
        .delete()
        .eq('id', release.id);

      if (error) throw error;
      setReleases(prev => prev.filter(r => r.id !== release.id));
    } catch (err) {
      console.error('Failed to delete release:', err);
      alert('Error deleting release: ' + err.message);
    } finally {
      setActionLoading(null);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
        Loading application releases log...
      </div>
    );
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '32px', alignItems: 'start' }}>
      {/* Releases History */}
      <div className="admin-card" style={{ padding: '24px' }}>
        <h3 style={{ marginBottom: '16px' }}>Published Releases</h3>
        <div className="admin-table-container">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Version</th>
                <th>Release Notes</th>
                <th>Download Link</th>
                <th>Type</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {releases.map((release) => (
                <tr key={release.id}>
                  <td>
                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                      <span style={{ fontWeight: 700 }}>v{release.version_name}</span>
                      <span style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '2px' }}>Code: {release.version_code}</span>
                    </div>
                  </td>
                  <td style={{ fontSize: '0.8rem', color: '#475569', maxWidth: '180px', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }} title={release.whats_new}>
                    {release.whats_new}
                  </td>
                  <td>
                    <a 
                      href={release.download_url} 
                      target="_blank" 
                      rel="noopener noreferrer"
                      style={{ fontSize: '0.8rem', color: '#ff8c00', textDecoration: 'none', fontWeight: 600 }}
                    >
                      Download APK
                    </a>
                  </td>
                  <td>
                    <span className={`admin-badge ${release.is_forced ? 'suspended' : 'active'}`} style={{ fontSize: '0.7rem' }}>
                      {release.is_forced ? 'Forced' : 'Optional'}
                    </span>
                  </td>
                  <td>
                    <div className="admin-actions-cell">
                      <button
                        className="admin-btn-action suspend"
                        disabled={actionLoading !== null}
                        onClick={() => handleDeleteRelease(release)}
                      >
                        {actionLoading === release.id ? 'Deleting...' : 'Delete'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {releases.length === 0 && (
                <tr>
                  <td colSpan="5" style={{ textAlign: 'center', color: '#94a3b8', padding: '32px' }}>
                    No releases published yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* APK Publisher Form */}
      <div className="admin-card" style={{ padding: '24px' }}>
        <h3 style={{ marginBottom: '16px' }}>Publish New APK Release</h3>
        <form onSubmit={handleSubmitRelease}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '12px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Version Code</label>
              <input
                type="number"
                required
                className="admin-search-input"
                style={{ paddingLeft: '16px' }}
                placeholder="e.g. 104"
                value={versionCode}
                onChange={(e) => setVersionCode(e.target.value)}
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>Version Name</label>
              <input
                type="text"
                required
                className="admin-search-input"
                style={{ paddingLeft: '16px' }}
                placeholder="e.g. 1.0.4"
                value={versionName}
                onChange={(e) => setVersionName(e.target.value)}
              />
            </div>
          </div>

          <div style={{ marginBottom: '16px' }}>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '6px' }}>What's New</label>
            <textarea
              className="admin-search-input"
              style={{ paddingLeft: '16px', height: '90px', resize: 'none', paddingTop: '10px' }}
              placeholder="List down bug fixes, new features, or updates in this version..."
              value={whatsNew}
              onChange={(e) => setWhatsNew(e.target.value)}
              required
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '20px' }}>
            <input
              type="checkbox"
              id="isForced"
              checked={isForced}
              onChange={(e) => setIsForced(e.target.checked)}
              style={{ width: '16px', height: '16px', cursor: 'pointer' }}
            />
            <label htmlFor="isForced" style={{ fontSize: '0.85rem', color: '#334155', fontWeight: 600, cursor: 'pointer' }}>
              Force Update (user must update to continue using the app)
            </label>
          </div>

          {/* Drag & Drop File Zone */}
          <div
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            style={{
              border: `2px dashed ${dragging ? '#ff8c00' : 'rgba(0, 0, 0, 0.1)'}`,
              borderRadius: '16px',
              padding: '28px',
              textAlign: 'center',
              backgroundColor: dragging ? 'rgba(255, 140, 0, 0.02)' : '#faf9f6',
              cursor: 'pointer',
              transition: 'all 0.25s ease',
              marginBottom: '24px'
            }}
            onClick={() => document.getElementById('apk-file-picker').click()}
          >
            <input
              type="file"
              id="apk-file-picker"
              accept=".apk"
              style={{ display: 'none' }}
              onChange={handleFileChange}
            />
            
            <svg 
              xmlns="http://www.w3.org/2000/svg" 
              fill="none" 
              viewBox="0 0 24 24" 
              stroke="currentColor"
              style={{ width: '40px', height: '40px', color: '#ff8c00', marginBottom: '8px', opacity: 0.85 }}
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
            </svg>

            {apkFile ? (
              <div>
                <span style={{ fontWeight: 600, color: '#0f172a', display: 'block', fontSize: '0.9rem' }}>{apkFile.name}</span>
                <span style={{ fontSize: '0.75rem', color: '#64748b' }}>{(apkFile.size / (1024 * 1024)).toFixed(2)} MB</span>
              </div>
            ) : (
              <div>
                <span style={{ fontWeight: 600, color: '#475569', display: 'block', fontSize: '0.85rem' }}>Drag & Drop APK here</span>
                <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>or click to browse files</span>
              </div>
            )}
          </div>

          {uploadProgress > 0 && (
            <div style={{ marginBottom: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.78rem', color: '#ff8c00', fontWeight: 600, marginBottom: '6px' }}>
                <span>Uploading Release Package...</span>
                <span>{uploadProgress}%</span>
              </div>
              <div style={{ height: '6px', backgroundColor: '#e2e8f0', borderRadius: '4px', overflow: 'hidden' }}>
                <div style={{ width: `${uploadProgress}%`, height: '100%', backgroundColor: '#ff8c00', transition: 'width 0.2s ease' }} />
              </div>
            </div>
          )}

          <button
            type="button"
            onClick={() => setShowPreview(true)}
            style={{
              width: '100%',
              padding: '12px',
              backgroundColor: 'transparent',
              border: '2px solid #ff8c00',
              color: '#ff8c00',
              borderRadius: '8px',
              fontSize: '15px',
              fontWeight: 600,
              cursor: 'pointer',
              marginBottom: '12px',
              transition: 'background-color 0.2s'
            }}
          >
            Show App Preview
          </button>

          <button
            type="submit"
            className="admin-btn-primary"
            style={{ width: '100%' }}
            disabled={actionLoading !== null}
          >
            {actionLoading === 'upload' ? 'Uploading & Syncing...' : 'Publish Release'}
          </button>
        </form>
      </div>

      {/* Live Phone Mockup Preview Modal */}
      {showPreview && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.75)',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          zIndex: 9999,
          backdropFilter: 'blur(4px)'
        }}>
          <div style={{
            position: 'relative',
            width: '380px',
            height: '760px',
            backgroundColor: '#000',
            borderRadius: '40px',
            padding: '12px',
            boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)',
            display: 'flex',
            flexDirection: 'column'
          }}>
            {/* Phone Screen Container */}
            <div style={{
              position: 'relative',
              width: '100%',
              height: '100%',
              backgroundColor: '#fff',
              borderRadius: '32px',
              overflow: 'hidden',
              display: 'flex',
              flexDirection: 'column',
              fontFamily: 'Inter, system-ui, -apple-system, sans-serif'
            }}>
              {/* Top-Right and Bottom-Left Corner Curve Decorators */}
              <div style={{
                position: 'absolute',
                top: 0,
                right: 0,
                width: '110px',
                height: '110px',
                background: 'linear-gradient(270deg, #FFC502 0%, #FB8500 100%)',
                zIndex: 0,
                borderBottomLeftRadius: '110px'
              }} />
              <div style={{
                position: 'absolute',
                bottom: 0,
                left: 0,
                width: '110px',
                height: '110px',
                background: 'linear-gradient(270deg, #FFC502 0%, #FB8500 100%)',
                zIndex: 0,
                borderTopRightRadius: '110px'
              }} />

              {/* Content Container */}
              <div style={{
                position: 'relative',
                zIndex: 1,
                width: '100%',
                height: '100%',
                backgroundColor: 'transparent',
                padding: '24px',
                display: 'flex',
                flexDirection: 'column',
                boxSizing: 'border-box'
              }}>
                {/* Header Logo and Label */}
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  marginBottom: '16px',
                  zIndex: 2
                }}>
                  <img 
                    src={launcherImg} 
                    alt="Presyohan Logo" 
                    style={{ width: '32px', height: '32px', objectFit: 'contain' }} 
                  />
                  <div style={{
                    display: 'flex',
                    flexDirection: 'column',
                    marginLeft: '6px',
                    lineHeight: 1
                  }}>
                    <span style={{
                      fontSize: '8px',
                      fontWeight: 'bold',
                      color: '#FFEB3B',
                      marginBottom: '-4px',
                      textTransform: 'lowercase'
                    }}>atong</span>
                    <div style={{ display: 'flex', fontWeight: 'bold', fontSize: '22px' }}>
                      <span style={{ color: '#FB8500' }}>presyo</span>
                      <span style={{ color: '#219EBC' }}>han?</span>
                    </div>
                  </div>
                </div>

                {/* Main Centered Content */}
                <div style={{
                  flex: 1,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  paddingTop: '24px'
                }}>
                  {/* Rocket Icon */}
                  <img 
                    src={rocketImg} 
                    alt="Rocket" 
                    style={{ width: '140px', height: '140px', objectFit: 'contain', marginBottom: '16px' }} 
                  />

                  {/* Title */}
                  <h2 style={{
                    fontSize: '28px',
                    fontWeight: 'bold',
                    color: '#FB8500',
                    margin: '0 0 6px 0',
                    textAlign: 'center'
                  }}>New Version</h2>

                  {/* Version Name */}
                  <span style={{
                    fontSize: '18px',
                    fontWeight: 'bold',
                    color: '#219EBC',
                    marginBottom: '32px'
                  }}>
                    V{versionName || '3.26.213'}
                  </span>

                  {/* Release Notes */}
                  <div style={{
                    alignSelf: 'stretch',
                    textAlign: 'left',
                    padding: '0 12px'
                  }}>
                    <h4 style={{
                      fontSize: '15px',
                      fontWeight: 'bold',
                      color: '#219EBC',
                      margin: '0 0 10px 0'
                    }}>Whats New?</h4>
                    
                    <div style={{
                      maxHeight: '150px',
                      overflowY: 'auto',
                      fontSize: '12.5px',
                      color: '#475569',
                      lineHeight: 1.6
                    }}>
                      {(whatsNew || 'List down bug fixes, new features, or updates in this version...')
                        .split('\n')
                        .filter(line => line.trim().length > 0)
                        .map((line, idx) => (
                          <div key={idx} style={{ display: 'flex', alignItems: 'flex-start', marginBottom: '6px' }}>
                            <span style={{ marginRight: '6px', color: '#219EBC' }}>•</span>
                            <span>{line.replace(/^•\s*/, '')}</span>
                          </div>
                        ))}
                    </div>
                  </div>
                </div>

                {/* Update Now Button */}
                <button type="button" style={{
                  width: '100%',
                  height: '48px',
                  backgroundColor: '#219EBC',
                  color: '#fff',
                  border: 'none',
                  borderRadius: '24px',
                  fontSize: '16px',
                  fontWeight: 'bold',
                  cursor: 'pointer',
                  boxShadow: '0 4px 10px rgba(33, 158, 188, 0.3)',
                  marginBottom: '12px'
                }}>
                  Update Now
                </button>
              </div>
            </div>

            {/* Close button outside phone frame */}
            <button 
              type="button"
              onClick={() => setShowPreview(false)}
              style={{
                position: 'absolute',
                top: '-20px',
                right: '-20px',
                width: '40px',
                height: '40px',
                borderRadius: '50%',
                backgroundColor: '#fff',
                border: 'none',
                color: '#334155',
                fontSize: '20px',
                fontWeight: 'bold',
                cursor: 'pointer',
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                boxShadow: '0 4px 10px rgba(0,0,0,0.1)'
              }}
            >
              ×
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
