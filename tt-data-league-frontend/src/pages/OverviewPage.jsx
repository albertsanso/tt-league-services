import AnalyticsBanner from '../components/overview/AnalyticsBanner.jsx'
import CommunityStats from '../components/overview/CommunityStats.jsx'
import GlobalSearch from '../components/overview/GlobalSearch.jsx'
import HeroBanner from '../components/overview/HeroBanner.jsx'
import QuickAccessGrid from '../components/overview/QuickAccessGrid.jsx'

function OverviewPage() {
  return (
    <div className="overview-page">
      <HeroBanner />
      <GlobalSearch />
      <QuickAccessGrid />
      <CommunityStats />
      <AnalyticsBanner />
    </div>
  )
}

export default OverviewPage
