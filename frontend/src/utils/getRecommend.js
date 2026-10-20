import { getCookie } from './getCookie';

const RECOMMEND_URL = `${process.env.REACT_APP_BACKEND_URL}/etf/recommend/`;

const getRecommend = async (etfCode) => {
    try {
        const response = await fetch(RECOMMEND_URL + etfCode + '/' + getCookie('userId'), { credentials: 'include' });
        if (!response.ok) {
            throw new Error('Network response was not ok');
        }
        const data = await response.json();
        return data;
    } catch (error) {
        throw error;
    }
};

export default getRecommend;
