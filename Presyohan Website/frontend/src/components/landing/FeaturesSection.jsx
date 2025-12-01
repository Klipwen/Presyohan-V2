import React, { useRef } from 'react'

// ==========================================
// 1. STANDARD ICONS (Stroke Based)
// ==========================================

const PackageIcon = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.5">
    <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
    <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
    <line x1="12" y1="22.08" x2="12" y2="12" />
  </svg>
)

const UsersIcon = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.5">
    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
    <circle cx="9" cy="7" r="4" />
    <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
    <path d="M16 3.13a4 4 0 0 1 0 7.75" />
  </svg>
)

const DatabaseIcon = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.5">
    <ellipse cx="12" cy="5" rx="9" ry="3" />
    <path d="M3 5v14a9 3 0 0 0 18 0V5" />
    <path d="M3 12a9 3 0 0 0 18 0" />
  </svg>
)

const LockIcon = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.5">
    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
  </svg>
)

const UploadIcon = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.5">
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <polyline points="17 8 12 3 7 8" />
    <line x1="12" y1="3" x2="12" y2="15" />
  </svg>
)

const DownloadIcon = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.5">
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <polyline points="7 10 12 15 17 10" />
    <line x1="12" y1="15" x2="12" y2="3" />
  </svg>
)

const BellIcon = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.5">
    <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
    <path d="M13.73 21a2 2 0 0 1-3.46 0" />
  </svg>
)

// ==========================================
// 2. CUSTOM ICONS (Group 1)
// ==========================================

const CrudIcon32 = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 32 32" xmlns="http://www.w3.org/2000/svg">
    <path fill="none" stroke={color} strokeWidth="2" strokeMiterlimit="10" d="M22.3,6.5l0.8-0.8c0.9-0.9,2.3-0.9,3.2,0c0.9,0.9,0.9,2.3,0,3.2l-0.8,0.8" />
    <line fill="none" stroke={color} strokeWidth="2" strokeMiterlimit="10" x1="18.9" y1="8.8" x2="23.2" y2="13.1" />
    <polyline fill="none" stroke={color} strokeWidth="2" strokeMiterlimit="10" points="10.8,25.6 10,22 6.4,21.2" />
    <path fill="none" stroke={color} strokeWidth="2" strokeMiterlimit="10" d="M10.5,25.9L5,27l1.1-5.5L21.7,5.9l4.4,4.4L10.5,25.9z" />
    <path d="M8.5,26.3L5,27l0.7-3.5L8.5,26.3z" fill={color} />
  </svg>
)

const NewCategoryIcon = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
    <path fill={color} d="M128 384v448h768V384H128zm-32-64h832a32 32 0 0 1 32 32v512a32 32 0 0 1-32 32H96a32 32 0 0 1-32-32V352a32 32 0 0 1 32-32zm64-128h704v64H160zm96-128h512v64H256z"/>
  </svg>
)

const SearchIcon31 = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 31 31" xmlns="http://www.w3.org/2000/svg">
    <g stroke="none" strokeWidth="1" fill={color} fillRule="nonzero">
      <g transform="translate(-2423, -153)">
        <g transform="translate(1350, 120)">
          <path d="M1093.53553,36.5649712 C1097.71545,40.744885 1098.19238,47.291526 1094.83227,52.0042063 L1103.43503,60.6066017 C1104.21608,61.3876503 1104.21608,62.6539803 1103.43503,63.4350288 C1102.65398,64.2160774 1101.38765,64.2160774 1100.60664,63.4350681 L1092.00344,54.8328202 L1091.75807,55.0031681 C1087.06576,58.1747947 1080.67257,57.6431328 1076.56497,53.5355339 C1071.87868,48.8492424 1071.87868,41.2512627 1076.56497,36.5649712 C1081.25126,31.8786797 1088.84924,31.8786797 1093.53553,36.5649712 Z M1085.50185,56.0410364 L1085.46749,56.0423857 C1085.36908,56.0460984 1085.27062,56.0484969 1085.17215,56.0495813 C1085.28312,56.0483436 1085.39251,56.0455011 1085.50185,56.0410364 Z M1083.92571,55.9929689 L1083.99021,55.9993672 C1084.11183,56.0110735 1084.23362,56.0207601 1084.35551,56.0284268 C1084.21196,56.0193955 1084.06872,56.0075797 1083.92571,55.9929689 Z M1082.87397,55.8340273 L1083.10847,55.8784908 C1083.24177,55.9022744 1083.37542,55.9235881 1083.50936,55.9424317 C1083.29688,55.912542 1083.08493,55.8763982 1082.87397,55.8340273 Z M1087.02951,55.8717388 L1086.93717,55.8881375 C1086.90085,55.8944288 1086.8645,55.900537 1086.82813,55.9064621 C1086.89513,55.8955498 1086.96236,55.8839579 1087.02951,55.8717388 Z M1081.42744,55.4392229 L1081.66395,55.5184993 C1082.01247,55.6309276 1082.3655,55.7255279 1082.72159,55.8023002 C1082.28483,55.7081462 1081.85251,55.5871062 1081.42744,55.4392229 Z M1088.0674,55.630457 L1087.80716,55.7009727 C1087.66357,55.737995 1087.51938,55.7720742 1087.37469,55.8032102 C1087.60722,55.7531661 1087.83816,55.6956027 1088.0674,55.630457 Z M1089.09194,55.2838364 C1089.04305,55.3031169 1088.99405,55.3220336 1088.94495,55.3405865 L1088.94477,55.3406552 Z M1080.68815,55.1515692 L1080.82883,55.2111106 C1080.78187,55.1916224 1080.73502,55.1717947 1080.68828,55.1516276 L1080.68815,55.1515692 Z M1089.63885,55.0507767 L1089.49896,55.1137571 C1089.42288,55.1473668 1089.3465,55.1800718 1089.26984,55.2118722 C1089.3934,55.1606247 1089.51653,55.1068817 1089.63885,55.0507767 Z M1080.13921,54.8964396 L1080.26945,54.9603441 L1080.26945,54.9603441 L1080.28027,54.9655541 C1080.23312,54.9428716 1080.1861,54.9198335 1080.13921,54.8964396 Z M1079.35192,54.4622393 L1079.46575,54.5303325 C1079.62151,54.6222331 1079.77904,54.7099662 1079.9382,54.7935317 C1079.74034,54.6896525 1079.54472,54.5791749 1079.35192,54.4622393 Z M1091.49243,53.9684576 L1091.34995,54.0699201 C1091.26583,54.1288254 1091.18106,54.1864166 1091.09568,54.2426936 L1091.1113,54.2323789 C1091.23976,54.1473855 1091.36684,54.0594118 1091.49243,53.9684576 Z M1078.68629,38.6862915 C1075.17157,42.2010101 1075.17157,47.8994949 1078.68629,51.4142136 C1082.20101,54.9289322 1087.89949,54.9289322 1091.41421,51.4142136 C1094.92893,47.8994949 1094.92893,42.2010101 1091.41421,38.6862915 C1087.89949,35.1715729 1082.20101,35.1715729 1078.68629,38.6862915 Z M1092.08905,53.5042914 L1091.84681,53.700719 L1091.84681,53.700719 L1091.86366,53.6874254 L1091.86366,53.6874254 L1092.08905,53.5042914 Z M1077.55437,53.1010123 L1077.48404,53.0348667 C1077.69477,53.234757 1077.91147,53.4247092 1078.13356,53.6047233 C1077.93602,53.4445998 1077.74286,53.2767322 1077.55437,53.1010123 Z M1077.27208,52.8284271 L1077.48404,53.0348667 L1077.49485,53.0451147 L1077.49485,53.0451147 L1077.27208,52.8284271 L1077.27208,52.8284271 Z" id="search"></path>
        </g>
      </g>
    </g>
  </svg>
)

// ==========================================
// 3. CUSTOM ICONS (Group 2)
// ==========================================

const InviteIcon = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 24 24" version="1.1" xmlns="http://www.w3.org/2000/svg">
    <g stroke="none" strokeWidth="1" fill="none" fillRule="evenodd">
      <g transform="translate(-864.000000, 0.000000)">
        <g transform="translate(864.000000, 0.000000)">
          <path d="M24,0 L24,24 L0,24 L0,0 L24,0 Z M12.5934901,23.257841 L12.5819402,23.2595131 L12.5108777,23.2950439 L12.4918791,23.2987469 L12.4918791,23.2987469 L12.4767152,23.2950439 L12.4056548,23.2595131 C12.3958229,23.2563662 12.3870493,23.2590235 12.3821421,23.2649074 L12.3780323,23.275831 L12.360941,23.7031097 L12.3658947,23.7234994 L12.3769048,23.7357139 L12.4804777,23.8096931 L12.4953491,23.8136134 L12.4953491,23.8136134 L12.5071152,23.8096931 L12.6106902,23.7357139 L12.6232938,23.7196733 L12.6232938,23.7196733 L12.6266527,23.7031097 L12.609561,23.275831 C12.6075724,23.2657013 12.6010112,23.2592993 12.5934901,23.257841 L12.5934901,23.257841 Z M12.8583906,23.1452862 L12.8445485,23.1473072 L12.6598443,23.2396597 L12.6498822,23.2499052 L12.6498822,23.2499052 L12.6471943,23.2611114 L12.6650943,23.6906389 L12.6699349,23.7034178 L12.6699349,23.7034178 L12.678386,23.7104931 L12.8793402,23.8032389 C12.8914285,23.8068999 12.9022333,23.8029875 12.9078286,23.7952264 L12.9118235,23.7811639 L12.8776777,23.1665331 C12.8752882,23.1545897 12.8674102,23.1470016 12.8583906,23.1452862 L12.8583906,23.1452862 Z M12.1430473,23.1473072 C12.1332178,23.1423925 12.1221763,23.1452606 12.1156365,23.1525954 L12.1099173,23.1665331 L12.0757714,23.7811639 C12.0751323,23.7926639 12.0828099,23.8018602 12.0926481,23.8045676 L12.108256,23.8032389 L12.3092106,23.7104931 L12.3186497,23.7024347 L12.3186497,23.7024347 L12.3225043,23.6906389 L12.340401,23.2611114 L12.337245,23.2485176 L12.337245,23.2485176 L12.3277531,23.2396597 L12.1430473,23.1473072 Z" fillRule="nonzero" />
          <path d="M17,3 C18.597725,3 19.903664,4.24892392 19.9949075,5.82372764 L20,6 L20,10.3501 L20.5939,10.0862 C21.2076,9.813435 21.9162954,10.2366962 21.9931452,10.8836127 L22,11 L22,19 C22,20.0543909 21.18415,20.9181678 20.1492661,20.9945144 L20,21 L4,21 C2.94563773,21 2.08183483,20.18415 2.00548573,19.1492661 L2,19 L2,11 C2,10.3284056 2.6746366,9.85267997 3.29700147,10.045194 L3.40614,10.0862 L4,10.3501 L4,6 C4,4.40232321 5.24892392,3.09633941 6.82372764,3.00509271 L7,3 L17,3 Z M20,12.5388 L12.8123,15.7333 C12.2951,15.9631 11.7049,15.9631 11.1877,15.7333 L4,12.5388 L4,19 L20,19 L20,12.5388 Z M17,5 L7,5 C6.44772,5 6,5.44772 6,6 L6,11.239 L12,13.9057 L18,11.239 L18,6 C18,5.44772 17.5523,5 17,5 Z M12,8 C12.5523,8 13,8.44772 13,9 C13,9.51283143 12.613973,9.93550653 12.1166239,9.9932722 L12,10 L10,10 C9.44772,10 9,9.55228 9,9 C9,8.48716857 9.38604429,8.06449347 9.88337975,8.0067278 L10,8 L12,8 Z" fill={color} />
        </g>
      </g>
    </g>
  </svg>
)

const TransferIcon = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 52 52" xmlns="http://www.w3.org/2000/svg">
    <g>
      <path fill={color} d="M48.5,2h-3C44.7,2,44,2.7,44,3.5v7c0,0.9-1,1.5-1.6,0.8l0,0C37.7,6.1,31,3.4,23.7,4.1c-2.6,0.2-5.1,1-7.4,2.2c-1.2,0.6-2.4,1.3-3.4,2.1c-0.7,0.5-0.8,1.6-0.2,2.3l2.1,2.1c0.5,0.5,1.3,0.6,1.9,0.2c1.2-0.8,2.5-1.5,3.9-2.1c0.6-0.2,1.3-0.4,2-0.6c6.3-1.2,12.3,1.3,15.7,5.4c1.2,1.4,0.3,2.3-0.7,2.3h-7c-0.8,0-1.6,0.7-1.6,1.5v3c0,0.8,0.8,1.5,1.6,1.5h18.2c0.7,0,1.2-0.6,1.2-1.3V3.5C50,2.7,49.3,2,48.5,2z"/>
      <path fill={color} d="M39.4,37.4c-0.6-0.6-1.5-0.6-2.1,0c-1.6,1.6-3.6,2.9-5.8,3.7c-0.6,0.2-1.3,0.4-2,0.6c-6.3,1.2-12.3-1.3-15.7-5.4c-1.2-1.4-0.3-2.3,0.7-2.3h7c0.8,0,1.5-0.7,1.5-1.5v-3c0-0.8-0.7-1.5-1.5-1.5H3.3C2.6,28,2,28.6,2,29.3v19.2C2,49.3,2.7,50,3.5,50h3C7.3,50,8,49.3,8,48.5v-7c0-0.9,1-1.5,1.6-0.8l0,0c4.6,5.2,11.4,7.9,18.7,7.2c2.6-0.2,5.1-1,7.4-2.2c2.2-1.1,4.1-2.5,5.7-4.1c0.6-0.6,0.6-1.6,0-2.1L39.4,37.4z"/>
    </g>
  </svg>
)

const StoreIcon = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path d="M4 3h16v2H4V3zm0 4h18v8h-2v6h-2v-6h-4v6H4v-6H2V7h2zm8 12v-4H6v4h6zm0-6h8V9H4v4h8z" fill={color}/>
  </svg>
)

// ==========================================
// 4. CUSTOM ICONS (Group 3)
// ==========================================

const ExportIcon = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 15 15" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path d="M3.5 12.5H1.5C0.947715 12.5 0.5 12.0523 0.5 11.5V7.5C0.5 6.94772 0.947715 6.5 1.5 6.5H13.5C14.0523 6.5 14.5 6.94772 14.5 7.5V11.5C14.5 12.0523 14.0523 12.5 13.5 12.5H11.5M3.5 6.5V1.5C3.5 0.947715 3.94772 0.5 4.5 0.5H10.5C11.0523 0.5 11.5 0.947715 11.5 1.5V6.5M3.5 10.5H11.5V14.5H3.5V10.5Z" stroke={color}/>
  </svg>
)

const CopyPricesIcon = ({ color }) => (
  <svg width="48" height="48" viewBox="0 0 15 15" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path
      fillRule="evenodd"
      clipRule="evenodd"
      d="M1 9.50006C1 10.3285 1.67157 11.0001 2.5 11.0001H4L4 10.0001H2.5C2.22386 10.0001 2 9.7762 2 9.50006L2 2.50006C2 2.22392 2.22386 2.00006 2.5 2.00006L9.5 2.00006C9.77614 2.00006 10 2.22392 10 2.50006V4.00002H5.5C4.67158 4.00002 4 4.67159 4 5.50002V12.5C4 13.3284 4.67158 14 5.5 14H12.5C13.3284 14 14 13.3284 14 12.5V5.50002C14 4.67159 13.3284 4.00002 12.5 4.00002H11V2.50006C11 1.67163 10.3284 1.00006 9.5 1.00006H2.5C1.67157 1.00006 1 1.67163 1 2.50006V9.50006ZM5 5.50002C5 5.22388 5.22386 5.00002 5.5 5.00002H12.5C12.7761 5.00002 13 5.22388 13 5.50002V12.5C13 12.7762 12.7761 13 12.5 13H5.5C5.22386 13 5 12.7762 5 12.5V5.50002Z"
      fill={color}
    />
  </svg>
)

const NotificationIcon = ({ color }) => (
  <svg fill={color} width="48" height="48" viewBox="0 0 36 36" version="1.1" preserveAspectRatio="xMidYMid meet" xmlns="http://www.w3.org/2000/svg">
    <path className="clr-i-outline clr-i-outline-path-1" d="M32.51,27.83A14.4,14.4,0,0,1,30,24.9a12.63,12.63,0,0,1-1.35-4.81V15.15A10.81,10.81,0,0,0,19.21,4.4V3.11a1.33,1.33,0,1,0-2.67,0V4.42A10.81,10.81,0,0,0,7.21,15.15v4.94A12.63,12.63,0,0,1,5.86,24.9a14.4,14.4,0,0,1-2.47,2.93,1,1,0,0,0-.34.75v1.36a1,1,0,0,0,1,1h27.8a1,1,0,0,0,1-1V28.58A1,1,0,0,0,32.51,27.83ZM5.13,28.94a16.17,16.17,0,0,0,2.44-3,14.24,14.24,0,0,0,1.65-5.85V15.15a8.74,8.74,0,1,1,17.47,0v4.94a14.24,14.24,0,0,0,1.65,5.85,16.17,16.17,0,0,0,2.44,3Z"></path>
    <path className="clr-i-outline clr-i-outline-path-2" d="M18,34.28A2.67,2.67,0,0,0,20.58,32H15.32A2.67,2.67,0,0,0,18,34.28Z"></path>
    <rect x="0" y="0" width="36" height="36" fillOpacity="0"/>
  </svg>
)

// ==========================================
// 5. MAIN COMPONENT
// ==========================================

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

  // --- ICON SELECTION LOGIC ---
  const getIconForTitle = (title, color) => {
    const t = String(title || '').toLowerCase()
    
    // Group 1: Product & Category
    if (t.includes('product & item crud') || (t.includes('product') && t.includes('crud'))) return <CrudIcon32 color={color} />
    if (t.includes('category management')) return <NewCategoryIcon color={color} />
    if (t.includes('advanced search') || t.includes('search')) return <SearchIcon31 color={color} />
    
    // Group 2: Team & Store
    if (t.includes('invitation') || t.includes('member')) return <InviteIcon color={color} />
    if (t.includes('transfer') || t.includes('ownership')) return <TransferIcon color={color} />
    if (t.includes('management') || t.includes('store')) return <StoreIcon color={color} />
    if (t.includes('access') || t.includes('role')) return <LockIcon color={color} />
    
    // Group 3: Data & Ops
    if (t.includes('import') || t.includes('bulk')) return <UploadIcon color={color} />
    if (t.includes('export') || t.includes('print')) return <ExportIcon color={color} />
    if (t.includes('copy')) return <CopyPricesIcon color={color} />
    if (t.includes('notification') || t.includes('activity')) return <NotificationIcon color={color} />
    if (t.includes('download')) return <DownloadIcon color={color} />
    
    // Fallback
    return <PackageIcon color={color} />
  }

  return (
    <section id="features" className="lp-features lp-section">

      <div className="lp-container">
        <h2 className="lp-section-title">Comprehensive Platform Features</h2>
        <p className="lp-section-text">
          Presyohan is more than just a price list—it's a comprehensive platform designed to give Store Owners and Managers complete control over pricing, inventory data, and staff access.
        </p>

        {/* GROUP 1: ORANGE ICONS AND TEXT */}
        <div className="lp-feature-group">
          <div className="lp-feature-head">
            <div className="lp-icon-box orange">
              <PackageIcon color="#ff8c00" />
            </div>
            {/* Added style for Orange Text */}
            <h3 className="lp-feature-title" style={{ color: '#ff8c00' }}>Unified Product &amp; Category Management</h3>
          </div>
          <p className="lp-section-text">Manage your entire product catalog efficiently with robust Create, Read, Update, and Delete (CRUD) functionality.</p>
          <div className="lp-grid">
            {feature1Cards.map((item, index) => (
              <div key={index} className="lp-mini-card" onClick={handleCardClick}>
                <div className="lp-mini-icon">
                  {getIconForTitle(item.title, '#ff8c00')}
                </div>
                <h4 className="lp-mini-title">{item.title}</h4>
                <p className="lp-mini-text">{item.description}</p>
              </div>
            ))}
          </div>
        </div>

        {/* GROUP 2: TEAL ICONS AND TEXT */}
        <div className="lp-feature-group">
          <div className="lp-feature-head">
            <div className="lp-icon-box teal">
              <UsersIcon color="#00bcd4" />
            </div>
            {/* Added style for Teal Text */}
            <h3 className="lp-feature-title" style={{ color: '#00bcd4' }}>Team &amp; Store Control</h3>
          </div>
          <p className="lp-section-text">Maintain security and accountability with precise, role-based controls over who can access and modify store data.</p>
          <div className="lp-grid">
            {feature2Cards.map((item, index) => (
              <div key={index} className="lp-mini-card" onClick={handleCardClick}>
                <div className="lp-mini-icon">
                  {getIconForTitle(item.title, '#00bcd4')}
                </div>
                <h4 className="lp-mini-title">{item.title}</h4>
                <p className="lp-mini-text">{item.description}</p>
              </div>
            ))}
          </div>
        </div>

        {/* GROUP 3: ORANGE ICONS AND TEXT */}
        <div className="lp-feature-group">
          <div className="lp-feature-head">
            <div className="lp-icon-box orange">
              <DatabaseIcon color="#ff8c00" />
            </div>
            {/* Added style for Orange Text */}
            <h3 className="lp-feature-title" style={{ color: '#ff8c00' }}>Data Operations &amp; Auditing</h3>
          </div>
          <p className="lp-section-text">Move beyond manual data entry with powerful bulk tools and clear audit trails.</p>
          <div className="lp-grid">
            {feature3Cards.map((item, index) => (
              <div key={index} className="lp-mini-card" onClick={handleCardClick}>
                <div className="lp-mini-icon">
                  {getIconForTitle(item.title, '#ff8c00')}
                </div>
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