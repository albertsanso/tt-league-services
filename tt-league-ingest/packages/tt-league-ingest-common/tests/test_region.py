import pytest

from ingest_common.region import Region, RegionType, region_for, region_for_territory
from ingest_common.source import Source


def test_vocabularies_are_closed():
    assert [r.value for r in Region] == ["espana", "catalunya", "barcelona", "girona", "lleida", "tarragona"]
    assert [r.value for r in RegionType] == ["nacional", "autonomica", "provincial", "comarcal"]
    assert Region.parse(" Girona ") is Region.GIRONA
    with pytest.raises(ValueError, match="unknown region 'mars'"):
        Region.parse("mars")
    with pytest.raises(ValueError, match="unknown regionType"):
        RegionType.parse("local")


def test_rfetm_and_fctt_are_constant():
    assert region_for(Source.RFETM) == (Region.ESPANA, RegionType.NACIONAL)
    assert region_for(Source.FCTT, "anything") == (Region.CATALUNYA, RegionType.AUTONOMICA)


@pytest.mark.parametrize("category, expected", [
    ("RTB PREFERENT", (Region.BARCELONA, RegionType.PROVINCIAL)),
    ("rtg-preferent", (Region.GIRONA, RegionType.PROVINCIAL)),
    ("RTL 1a COMARCAL", (Region.LLEIDA, RegionType.COMARCAL)),
    ("rtt-2a-comarcal", (Region.TARRAGONA, RegionType.COMARCAL)),
])
def test_bcnesa_region_from_category_prefix(category, expected):
    assert region_for(Source.BCNESA, category) == expected


@pytest.mark.parametrize("category", [None, "", "PREFERENT", "XYZ 1a"])
def test_bcnesa_unknown_prefix_fails(category):
    with pytest.raises(ValueError, match="cannot derive the region"):
        region_for(Source.BCNESA, category)


def test_territory_maps_to_region():
    assert region_for_territory("Barcelona") is Region.BARCELONA
    assert region_for_territory("Catalunya") is Region.CATALUNYA
    with pytest.raises(ValueError, match="unknown territory"):
        region_for_territory("Madrid")
