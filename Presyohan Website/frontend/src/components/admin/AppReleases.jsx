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

    const vCodeInt = parseInt(versionCode);
    if (releases.some(r => r.version_code === vCodeInt)) {
      alert(`A release with Version Code ${versionCode} already exists.`);
      return;
    }

    try {
      setActionLoading('upload');
      setUploadProgress(10);

      // 1. Upload APK file to Supabase Storage
      const cleanVersionName = versionName.trim().toLowerCase().startsWith('v')
        ? versionName.trim().substring(1)
        : versionName.trim();
      const filename = `releases/presyohan-v${cleanVersionName}.apk`;
      
      setUploadProgress(30);
      const { error: uploadErr } = await supabase.storage
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

      // Clean up oldest versions to keep only 5
      try {
        const { data: currentReleases, error: listErr } = await supabase
          .from('app_releases')
          .select('*')
          .order('version_code', { ascending: true });

        if (!listErr && currentReleases && currentReleases.length > 5) {
          const deleteCount = currentReleases.length - 5;
          const oldestToDelete = currentReleases.slice(0, deleteCount);

          for (const oldRelease of oldestToDelete) {
            const cleanOldVersionName = oldRelease.version_name.trim().toLowerCase().startsWith('v')
              ? oldRelease.version_name.trim().substring(1)
              : oldRelease.version_name.trim();
            const oldFilenameByName = `releases/presyohan-v${cleanOldVersionName}.apk`;
            const oldFilenameByCode = `releases/presyohan-v${oldRelease.version_code}.apk`;
            await supabase.storage
              .from('presyohan.apk')
              .remove([oldFilenameByName, oldFilenameByCode]);

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

      const cleanVersionName = release.version_name.trim().toLowerCase().startsWith('v')
        ? release.version_name.trim().substring(1)
        : release.version_name.trim();
      const filenameByName = `releases/presyohan-v${cleanVersionName}.apk`;
      const filenameByCode = `releases/presyohan-v${release.version_code}.apk`;
      await supabase.storage
        .from('presyohan.apk')
        .remove([filenameByName, filenameByCode]);

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

  // Compute metrics
  const latestRelease = releases[0] || null;
  const totalReleases = releases.length;
  const forcedCount = releases.filter(r => r.is_forced).length;
  const optionalCount = totalReleases - forcedCount;

  if (loading) {
    return (
      <div style={{ padding: '48px', textAlign: 'center', color: '#64748b', fontFamily: 'Inter, sans-serif' }}>
        Loading application releases log...
      </div>
    );
  }

  return (
    <div style={{ 
      display: 'flex', 
      flexDirection: 'column', 
      gap: '32px', 
      fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
      color: '#334155',
      maxWidth: '1280px',
      margin: '0 auto',
      padding: '8px'
    }}>
      
      {/* SECTION 1: Two Column workspace (Form + Live Phone Mockup) */}
      <div style={{ 
        display: 'grid', 
        gridTemplateColumns: '1fr 340px', 
        gap: '32px', 
        alignItems: 'start' 
      }}>
        
        {/* Publisher Form Panel */}
        <div style={{ 
          backgroundColor: '#ffffff', 
          borderRadius: '20px', 
          padding: '30px', 
          boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.05)',
          border: '1px solid #f1f5f9'
        }}>
          <div style={{ borderBottom: '1px solid #f1f5f9', paddingBottom: '16px', marginBottom: '24px' }}>
            <h2 style={{ margin: '0 0 6px 0', fontSize: '1.5rem', fontWeight: 800, color: '#0f172a' }}>
              Publish App Release
            </h2>
            <p style={{ margin: 0, fontSize: '0.875rem', color: '#64748b' }}>
              Deploy Android package binaries (.apk) to production storage and sync local upgrade settings.
            </p>
          </div>

          <form onSubmit={handleSubmitRelease} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            
            {/* Form details section */}
            <div>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 800, color: '#475569', marginBottom: '8px', letterSpacing: '0.5px' }}>
                1. RELEASE METADATA
              </label>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', color: '#64748b', fontWeight: 600, marginBottom: '4px' }}>Version Code (Integer)</label>
                  <input
                    type="number"
                    required
                    className="admin-search-input"
                    style={{ paddingLeft: '16px', height: '42px', fontSize: '0.9rem', borderRadius: '8px' }}
                    placeholder="e.g. 104"
                    value={versionCode}
                    onChange={(e) => setVersionCode(e.target.value)}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', color: '#64748b', fontWeight: 600, marginBottom: '4px' }}>Version Name (String)</label>
                  <input
                    type="text"
                    required
                    className="admin-search-input"
                    style={{ paddingLeft: '16px', height: '42px', fontSize: '0.9rem', borderRadius: '8px' }}
                    placeholder="e.g. 1.0.4"
                    value={versionName}
                    onChange={(e) => setVersionName(e.target.value)}
                  />
                </div>
              </div>
            </div>

            {/* Changelog field */}
            <div>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 800, color: '#475569', marginBottom: '6px', letterSpacing: '0.5px' }}>
                2. RELEASE CHANGELOG (WHATS NEW?)
              </label>
              <textarea
                className="admin-search-input"
                style={{ paddingLeft: '16px', height: '90px', resize: 'none', paddingTop: '12px', fontSize: '0.9rem', borderRadius: '8px' }}
                placeholder="List bug fixes, visual improvements, or new modules in this update..."
                value={whatsNew}
                onChange={(e) => setWhatsNew(e.target.value)}
                required
              />
            </div>

            {/* Custom switch toggler for isForced */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 18px', backgroundColor: isForced ? '#fef2f2' : '#f0fdf4', borderRadius: '12px', border: isForced ? '1px solid #fee2e2' : '1px solid #dcfce7', transition: 'all 0.2s' }}>
              <div>
                <span style={{ display: 'block', fontSize: '0.875rem', fontWeight: 700, color: isForced ? '#991b1b' : '#14532d' }}>Force Upgrade Blockout</span>
                <span style={{ fontSize: '0.75rem', color: isForced ? '#b91c1c' : '#166534' }}>
                  {isForced ? 'Users cannot skip this update and must upgrade to continue' : 'Optional update (users can press "Later" to skip)'}
                </span>
              </div>
              <div 
                style={{
                  width: '50px',
                  height: '26px',
                  borderRadius: '13px',
                  backgroundColor: isForced ? '#ef4444' : '#cbd5e1',
                  position: 'relative',
                  cursor: 'pointer',
                  transition: 'background-color 0.2s'
                }}
                onClick={() => setIsForced(!isForced)}
              >
                <div style={{
                  width: '20px',
                  height: '20px',
                  borderRadius: '50%',
                  backgroundColor: '#ffffff',
                  position: 'absolute',
                  top: '3px',
                  left: isForced ? '27px' : '3px',
                  transition: 'left 0.2s',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.15)'
                }} />
              </div>
            </div>

            {/* Drag & drop file picker zone */}
            <div>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 800, color: '#475569', marginBottom: '8px', letterSpacing: '0.5px' }}>
                3. UPLOAD BINARY FILE (.APK)
              </label>
              <div
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
                style={{
                  border: `2px dashed ${dragging ? '#ff8c00' : '#cbd5e1'}`,
                  borderRadius: '16px',
                  padding: '30px 20px',
                  textAlign: 'center',
                  backgroundColor: dragging ? 'rgba(251, 133, 0, 0.03)' : '#fafafa',
                  cursor: 'pointer',
                  transition: 'all 0.2s ease',
                  boxShadow: 'inset 0 1px 2px rgba(0,0,0,0.02)'
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
                  style={{ width: '38px', height: '38px', color: '#ff8c00', marginBottom: '8px', opacity: 0.8 }}
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
                </svg>

                {apkFile ? (
                  <div>
                    <span style={{ fontWeight: 700, color: '#0f172a', display: 'block', fontSize: '0.85rem' }}>{apkFile.name}</span>
                    <span style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>{(apkFile.size / (1024 * 1024)).toFixed(2)} MB</span>
                  </div>
                ) : (
                  <div>
                    <span style={{ fontWeight: 700, color: '#475569', display: 'block', fontSize: '0.8rem' }}>Drag & Drop APK package here</span>
                    <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>or click to browse local files</span>
                  </div>
                )}
              </div>
            </div>

            {/* Progress bar indicator */}
            {uploadProgress > 0 && (
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.78rem', color: '#ff8c00', fontWeight: 700, marginBottom: '4px' }}>
                  <span>Uploading Binary package...</span>
                  <span>{uploadProgress}%</span>
                </div>
                <div style={{ height: '6px', backgroundColor: '#e2e8f0', borderRadius: '4px', overflow: 'hidden' }}>
                  <div style={{ width: `${uploadProgress}%`, height: '100%', backgroundColor: '#ff8c00', transition: 'width 0.1s ease' }} />
                </div>
              </div>
            )}

            {/* Launch action button */}
            <button
              type="submit"
              style={{
                height: '46px',
                borderRadius: '8px',
                border: 'none',
                color: '#ffffff',
                backgroundColor: '#ff8c00',
                fontWeight: 800,
                cursor: 'pointer',
                fontSize: '0.95rem',
                boxShadow: '0 4px 6px -1px rgba(251, 133, 0, 0.2)'
              }}
              disabled={actionLoading !== null}
            >
              {actionLoading === 'upload' ? 'Uploading & Syncing...' : 'Publish Release'}
            </button>
          </form>
        </div>

        {/* Real-time Embedded Phone Mockup */}
        <div style={{ 
          position: 'sticky', 
          top: '24px', 
          display: 'flex', 
          flexDirection: 'column', 
          alignItems: 'center',
          gap: '12px',
          boxSizing: 'border-box'
        }}>
          <span style={{ fontSize: '0.75rem', fontWeight: 800, textTransform: 'uppercase', color: '#64748b', letterSpacing: '0.8px' }}>
            📱 RELEASE DIALOG PREVIEW
          </span>

          <div style={{
            width: '300px',
            height: '560px',
            backgroundColor: '#0f172a',
            borderRadius: '40px',
            padding: '12px',
            boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
            position: 'relative',
            display: 'flex',
            flexDirection: 'column',
            boxSizing: 'border-box',
            border: '2px solid #334155'
          }}>
            {/* Notch */}
            <div style={{
              width: '100px',
              height: '18px',
              backgroundColor: '#0f172a',
              borderBottomLeftRadius: '12px',
              borderBottomRightRadius: '12px',
              position: 'absolute',
              top: '12px',
              left: '50%',
              transform: 'translateX(-50%)',
              zIndex: 10,
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center'
            }}>
              <div style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#1e293b' }} />
            </div>

            {/* Screen layout */}
            <div style={{
              flex: 1,
              backgroundColor: '#e2e8f0', // standard screen backdrop behind popup modal
              borderRadius: '28px',
              overflow: 'hidden',
              position: 'relative',
              display: 'flex',
              flexDirection: 'column',
              boxSizing: 'border-box'
            }}>
              
              {/* Corner curves decorators (simulate SplashActivity background behind dialog) */}
              <div style={{
                position: 'absolute',
                top: 0,
                right: 0,
                width: '80px',
                height: '80px',
                background: 'linear-gradient(270deg, #FFC502 0%, #FB8500 100%)',
                zIndex: 0,
                borderBottomLeftRadius: '80px',
                opacity: 0.9
              }} />
              <div style={{
                position: 'absolute',
                bottom: 0,
                left: 0,
                width: '80px',
                height: '80px',
                background: 'linear-gradient(270deg, #FFC502 0%, #FB8500 100%)',
                zIndex: 0,
                borderTopRightRadius: '80px',
                opacity: 0.9
              }} />

              {/* Status bar mockup */}
              <div style={{
                position: 'relative',
                height: '24px',
                padding: '0 16px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                fontSize: '9px',
                color: isForced ? '#64748b' : '#334155',
                fontWeight: 600,
                zIndex: 10
              }}>
                <span>06:00</span>
                <div style={{ display: 'flex', gap: '3px' }}>
                  <span>📶</span>
                  <span>🔋</span>
                </div>
              </div>

              {isForced ? (
                // Forced update: Full screen view overlay
                <div style={{ 
                  position: 'relative', 
                  zIndex: 2, 
                  display: 'flex', 
                  flexDirection: 'column', 
                  flex: 1, 
                  padding: '16px',
                  boxSizing: 'border-box',
                  justifyContent: 'space-between'
                }}>
                  {/* Launcher logo */}
                  <div style={{ display: 'flex', alignItems: 'center' }}>
                    <img 
                      src={launcherImg} 
                      alt="Logo" 
                      style={{ width: '22px', height: '22px', objectFit: 'contain' }} 
                    />
                    <div style={{ display: 'flex', flexDirection: 'column', marginLeft: '4px', lineHeight: 1 }}>
                      <span style={{ fontSize: '6px', fontWeight: 'bold', color: '#ffc107', marginBottom: '-2px' }}>atong</span>
                      <div style={{ display: 'flex', fontWeight: 'bold', fontSize: '15px' }}>
                        <span style={{ color: '#FB8500' }}>presyo</span>
                        <span style={{ color: '#219EBC' }}>han?</span>
                      </div>
                    </div>
                  </div>

                  {/* Rocket & details */}
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
                    <img 
                      src={rocketImg} 
                      alt="Rocket" 
                      style={{ width: '80px', height: '80px', objectFit: 'contain', marginBottom: '8px' }} 
                    />
                    <h3 style={{ fontSize: '18px', fontWeight: 900, color: '#FB8500', margin: '0 0 2px 0' }}>New Version</h3>
                    <span style={{ fontSize: '12px', fontWeight: 800, color: '#219EBC', marginBottom: '14px' }}>
                      V{versionName || '1.0.0'}
                    </span>

                    {/* Bullet points */}
                    <div style={{ alignSelf: 'stretch', textAlign: 'left', width: '100%', boxSizing: 'border-box' }}>
                      <h5 style={{ fontSize: '10.5px', fontWeight: 800, color: '#219EBC', margin: '0 0 6px 0' }}>Whats New?</h5>
                      <div style={{
                        maxHeight: '110px',
                        overflowY: 'auto',
                        fontSize: '9.5px',
                        color: '#475569',
                        lineHeight: 1.4
                      }}>
                        {(whatsNew || 'List down release features...')
                          .split('\n')
                          .filter(line => line.trim().length > 0)
                          .map((line, idx) => (
                            <div key={idx} style={{ display: 'flex', alignItems: 'flex-start', marginBottom: '4px' }}>
                              <span style={{ marginRight: '4px', color: '#219EBC' }}>•</span>
                              <span>{line.replace(/^•\s*/, '')}</span>
                            </div>
                          ))}
                      </div>
                    </div>
                  </div>

                  {/* Single orange action button */}
                  <button type="button" style={{
                    width: '100%',
                    height: '34px',
                    backgroundColor: '#FB8500',
                    color: '#ffffff',
                    border: 'none',
                    borderRadius: '17px',
                    fontSize: '11px',
                    fontWeight: 'bold',
                    cursor: 'pointer',
                    boxShadow: '0 4px 6px rgba(251, 133, 0, 0.2)',
                    marginBottom: '4px'
                  }}>
                    Update Now
                  </button>
                </div>
              ) : (
                // Optional update: floating dialog modal centered on screen with dimmed backdrop
                <div style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  right: 0,
                  bottom: 0,
                  backgroundColor: 'rgba(15, 23, 42, 0.4)', // Dimmed background overlay
                  display: 'flex',
                  justifyContent: 'center',
                  alignItems: 'center',
                  padding: '16px',
                  boxSizing: 'border-box',
                  zIndex: 5
                }}>
                  {/* Dialog Card Modal */}
                  <div style={{
                    width: '100%',
                    backgroundColor: '#ffffff',
                    borderRadius: '20px',
                    padding: '16px',
                    boxShadow: '0 10px 25px rgba(0, 0, 0, 0.2)',
                    display: 'flex',
                    flexDirection: 'column',
                    boxSizing: 'border-box'
                  }}>
                    {/* Header Logo */}
                    <div style={{ display: 'flex', alignItems: 'center', marginBottom: '10px' }}>
                      <img 
                        src={launcherImg} 
                        alt="Logo" 
                        style={{ width: '20px', height: '20px', objectFit: 'contain' }} 
                      />
                      <div style={{ display: 'flex', flexDirection: 'column', marginLeft: '4px', lineHeight: 1 }}>
                        <span style={{ fontSize: '5.5px', fontWeight: 'bold', color: '#ffc107', marginBottom: '-2px' }}>atong</span>
                        <div style={{ display: 'flex', fontWeight: 'bold', fontSize: '13px' }}>
                          <span style={{ color: '#FB8500' }}>presyo</span>
                          <span style={{ color: '#219EBC' }}>han?</span>
                        </div>
                      </div>
                    </div>

                    {/* Dialog content box */}
                    <div style={{
                      backgroundColor: '#EDF7FA',
                      borderRadius: '12px',
                      padding: '10px',
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      textAlign: 'center',
                      marginBottom: '10px',
                      boxSizing: 'border-box'
                    }}>
                      <img 
                        src={rocketImg} 
                        alt="Rocket" 
                        style={{ width: '56px', height: '56px', objectFit: 'contain', marginBottom: '4px' }} 
                      />
                      <h4 style={{ fontSize: '12px', fontWeight: 800, color: '#475569', margin: '0 0 2px 0' }}>New Version</h4>
                      <span style={{ fontSize: '10px', fontWeight: 700, color: '#219EBC', marginBottom: '6px' }}>
                        v{versionName || '1.0.0'}
                      </span>

                      {/* Changelog notes list */}
                      <div style={{ alignSelf: 'stretch', textAlign: 'left', width: '100%' }}>
                        <h5 style={{ fontSize: '9px', fontWeight: 800, color: '#219EBC', margin: '0 0 4px 0' }}>Whats New?</h5>
                        <div style={{
                          maxHeight: '60px',
                          overflowY: 'auto',
                          fontSize: '8.5px',
                          color: '#475569',
                          lineHeight: 1.3
                        }}>
                          {(whatsNew || 'Changelog features...')
                            .split('\n')
                            .filter(line => line.trim().length > 0)
                            .map((line, idx) => (
                              <div key={idx} style={{ display: 'flex', alignItems: 'flex-start', marginBottom: '2px' }}>
                                <span style={{ marginRight: '3px', color: '#219EBC' }}>•</span>
                                <span>{line.replace(/^•\s*/, '')}</span>
                              </div>
                            ))}
                        </div>
                      </div>
                    </div>

                    {/* Navigation Buttons: equal sized Teal & Orange */}
                    <div style={{ display: 'flex', gap: '6px' }}>
                      <button type="button" style={{
                        flex: 1,
                        height: '28px',
                        backgroundColor: '#219EBC',
                        color: '#ffffff',
                        border: 'none',
                        borderRadius: '14px',
                        fontSize: '9.5px',
                        fontWeight: 'bold',
                        cursor: 'pointer'
                      }}>
                        Later
                      </button>
                      <button type="button" style={{
                        flex: 1,
                        height: '28px',
                        backgroundColor: '#FB8500',
                        color: '#ffffff',
                        border: 'none',
                        borderRadius: '14px',
                        fontSize: '9.5px',
                        fontWeight: 'bold',
                        cursor: 'pointer',
                        boxShadow: '0 4px 6px rgba(251, 133, 0, 0.15)'
                      }}>
                        Update Now
                      </button>
                    </div>

                  </div>
                </div>
              )}

              {/* Bottom bar indicator */}
              <div style={{ 
                width: '90px', 
                height: '4px', 
                backgroundColor: '#334155', 
                borderRadius: '2px', 
                alignSelf: 'center', 
                position: 'absolute',
                bottom: '8px',
                left: '50%',
                transform: 'translateX(-50%)',
                zIndex: 10
              }} />
            </div>
          </div>
        </div>

      </div>

      {/* SECTION 2: Full Width Campaign Dashboard */}
      <div style={{ 
        backgroundColor: '#ffffff', 
        borderRadius: '20px', 
        padding: '30px', 
        boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.05)',
        border: '1px solid #f1f5f9'
      }}>
        
        {/* Metric Cards Grid */}
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(4, 1fr)', 
          gap: '16px', 
          marginBottom: '30px' 
        }}>
          <div style={{ backgroundColor: '#eff6ff', borderRadius: '12px', padding: '16px', border: '1px solid #dbeafe', display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#1e40af', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Total Releases</span>
            <span style={{ fontSize: '1.8rem', fontWeight: 800, color: '#1e3a8a', marginTop: '6px' }}>{totalReleases}</span>
          </div>
          <div style={{ backgroundColor: '#fef2f2', borderRadius: '12px', padding: '16px', border: '1px solid #fee2e2', display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#991b1b', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Forced Updates</span>
            <span style={{ fontSize: '1.8rem', fontWeight: 800, color: '#7f1d1d', marginTop: '6px' }}>{forcedCount}</span>
          </div>
          <div style={{ backgroundColor: '#ecfdf5', borderRadius: '12px', padding: '16px', border: '1px solid #d1fae5', display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#065f46', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Optional Updates</span>
            <span style={{ fontSize: '1.8rem', fontWeight: 800, color: '#064e3b', marginTop: '6px' }}>{optionalCount}</span>
          </div>
          <div style={{ backgroundColor: '#fffbeb', borderRadius: '12px', padding: '16px', border: '1px solid #fef3c7', display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#92400e', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Latest Version</span>
            <span style={{ fontSize: '1.4rem', fontWeight: 800, color: '#78350f', marginTop: '8px' }}>
              {latestRelease ? `v${latestRelease.version_name}` : 'N/A'}
            </span>
          </div>
        </div>

        {/* History table */}
        <div>
          <h3 style={{ margin: '0 0 16px 0', fontSize: '1.25rem', fontWeight: 800, color: '#0f172a' }}>Release History Log</h3>
          <div className="admin-table-container">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Release Version</th>
                  <th>Changelog / Whats New</th>
                  <th>Download Link</th>
                  <th>Classification</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {releases.map((release) => (
                  <tr key={release.id}>
                    <td>
                      <div style={{ display: 'flex', flexDirection: 'column' }}>
                        <span style={{ fontWeight: 800, color: '#1e293b' }}>v{release.version_name}</span>
                        <span style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '2px', fontWeight: 600 }}>Code: {release.version_code}</span>
                      </div>
                    </td>
                    <td>
                      <div 
                        style={{ fontSize: '0.85rem', color: '#475569', maxWidth: '280px', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }} 
                        title={release.whats_new}
                      >
                        {release.whats_new}
                      </div>
                    </td>
                    <td>
                      <a 
                        href={release.download_url} 
                        target="_blank" 
                        rel="noopener noreferrer"
                        style={{ fontSize: '0.8rem', color: '#ff8c00', textDecoration: 'none', fontWeight: 700 }}
                      >
                        Download APK File
                      </a>
                    </td>
                    <td>
                      <span 
                        className={`admin-badge ${release.is_forced ? 'suspended' : 'active'}`} 
                        style={{ 
                          fontSize: '0.75rem', 
                          fontWeight: 700, 
                          padding: '3px 8px', 
                          borderRadius: '4px',
                          textTransform: 'uppercase'
                        }}
                      >
                        {release.is_forced ? 'Forced Upgrade' : 'Optional Upgrade'}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <button
                        style={{
                          padding: '4px 10px',
                          borderRadius: '6px',
                          border: '1px solid #fee2e2',
                          backgroundColor: '#fee2e2',
                          color: '#ef4444',
                          fontWeight: 700,
                          cursor: 'pointer'
                        }}
                        disabled={actionLoading !== null}
                        onClick={() => handleDeleteRelease(release)}
                      >
                        {actionLoading === release.id ? 'Deleting...' : 'Delete'}
                      </button>
                    </td>
                  </tr>
                ))}
                {releases.length === 0 && (
                  <tr>
                    <td colSpan="5" style={{ textAlign: 'center', color: '#94a3b8', padding: '32px' }}>
                      No APK release logs registered. Upload an APK file above to get started.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

      </div>

    </div>
  );
}
