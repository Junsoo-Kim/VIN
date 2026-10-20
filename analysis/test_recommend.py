import os
import unittest

import pandas as pd

from recommend import recommend_stocks
from recommend_with_tendency import customized_recommend_stocks, folder_path

STOCKS = ['AAXJ', 'ACE 200']
DYNAMIC_KEY = '사용자 성향 추천 1'


def load_correlations(stock):
    return pd.read_csv(os.path.join(folder_path, f"{stock}_corr.csv"), index_col=0)['Correlation']


class RecommendWithTendencyTest(unittest.TestCase):

    def test_recommended_values_belong_to_recommended_etf(self):
        for stock in STOCKS:
            correlations = load_correlations(stock)
            for score in [-100, -50, -21, -10, 0, 10, 21, 50, 100]:
                for name, value in customized_recommend_stocks(stock, score).values():
                    self.assertAlmostEqual(correlations[name], value, msg=f"{stock} {score} {name}")

    def test_neutral_scores_do_not_fail(self):
        for score in [-20, -5, 0, 5, 20]:
            result = customized_recommend_stocks('AAXJ', score)
            self.assertIn(DYNAMIC_KEY, result)

    def test_higher_score_recommends_higher_correlation(self):
        for stock in STOCKS:
            values = [customized_recommend_stocks(stock, score)[DYNAMIC_KEY][1] for score in [-100, -50, 0, 50, 100]]
            self.assertEqual(values, sorted(values), msg=stock)

    def test_results_are_plain_python_types(self):
        for name, value in customized_recommend_stocks('AAXJ', 50).values():
            self.assertIs(type(name), str)
            self.assertIs(type(value), float)

    def test_missing_correlation_file_raises(self):
        with self.assertRaises(FileNotFoundError):
            customized_recommend_stocks('NOT_EXISTING_ETF', 0)


class RecommendTest(unittest.TestCase):

    def test_fixed_recommendations_cover_min_mid_max(self):
        correlations = load_correlations('AAXJ')
        result = recommend_stocks('AAXJ')
        values = sorted(value for _, value in result.values())
        self.assertAlmostEqual(values[0], correlations.min())
        self.assertAlmostEqual(values[-1], correlations.max())


if __name__ == '__main__':
    unittest.main()
