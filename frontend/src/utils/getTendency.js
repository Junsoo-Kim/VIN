import { getCookie } from './getCookie';

export const getTendency = async () => {
    const GET_TENDENCY_URL = `${process.env.REACT_APP_BACKEND_URL}/tendency/${getCookie('userId')}`;

    try {
        const response = await fetch(GET_TENDENCY_URL, { credentials: 'include' });
        if (!response.ok) {
            if (response.status === 404) {
                throw new Error('Tendency not found');
            }
            throw new Error('Failed to fetch tendency information');
        }
        return await response.json();
    } catch (error) {
        console.error('Error fetching tendency information:', error);
        return null;
    }
};
