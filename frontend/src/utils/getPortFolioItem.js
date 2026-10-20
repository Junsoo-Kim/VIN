import { getCookie } from './getCookie';

const PORTFOLIO_URL = process.env.REACT_APP_BACKEND_URL + '/portfolio/';

export const getPortfolioItem = async () => {
    const userId = getCookie('userId');

    try {
        const response = await fetch(PORTFOLIO_URL + userId, { credentials: 'include' });
        const data = await response.json();
        console.log(data);
        if (data === '[]') {
            return []; // 빈 배열 응답 처리
        }

        return data.map((item) => ({
            name: item.etfName,
            code: item.etfCode,
            quantity: Number(item.count),
            amount: Math.round(Number(item.money)),
            percentage: Number(item.percent).toFixed(2),
            country: item.country,
        }));
    } catch (error) {
        console.error('포트폴리오 데이터 요청 중 에러 발생:', error);
        throw error;
    }
};
