export const API_BASE_URL: string = 'https://api.example.invalid';

export interface ApiError {
  code: string;
  message: string;
  retryable: boolean;
}
