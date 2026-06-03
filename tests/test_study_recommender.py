from data_model import completed_example_response, worked_adc_incomplete_response
from flowchart import render_flowchart_png
from study_recommender import recommend_study_design


def test_incomplete_adc_recommends_adc_readiness_pathway():
    response = worked_adc_incomplete_response()

    recommendation = recommend_study_design(response)

    assert recommendation.pathway == "ADC-readiness confirmation followed by staged tolerability design"
    assert any("ADC" in item for item in recommendation.rationale)
    assert recommendation.flowchart_steps
    assert recommendation.procedure_items
    assert recommendation.welfare_costs
    assert "Replacement" in recommendation.three_rs


def test_repeat_dose_downstream_recommends_repeat_dose_pathway_when_ready():
    response = completed_example_response()
    response["downstream_study"] = "Repeat-dose toxicity study"
    response["downstream_schedule"] = "Multiple treatment cycles"
    response["mtd_schedule"] = "Multiple treatment cycles"

    recommendation = recommend_study_design(response)

    assert "Repeat-dose" in recommendation.pathway


def test_flowchart_renderer_returns_png_bytes():
    response = worked_adc_incomplete_response()
    recommendation = recommend_study_design(response)

    png = render_flowchart_png(recommendation.flowchart_steps)

    assert png.getbuffer().nbytes > 1000
    assert png.getvalue()[:8] == b"\x89PNG\r\n\x1a\n"
