/** 서버도 한 번 더 정규화하지만, 보내기 전에 프론트에서도 숫자만 남긴다. */
export function digitsOnly(value: string): string {
  return value.replace(/\D/g, "");
}

/** 입력 중 자동 하이픈. 010-1234-5678 */
export function formatPhone(value: string): string {
  const digits = digitsOnly(value).slice(0, 11);
  if (digits.length < 4) return digits;
  if (digits.length < 8) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}
