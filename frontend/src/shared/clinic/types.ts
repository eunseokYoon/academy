import { dayLabel } from "../date";

/**
 * "08-13 (목) 17:00~18:00".
 *
 * <p>학생(S-6·S-9)과 학부모(P-2)가 각자 조립하던 것을 한 곳으로 모았다.
 * 응답 타입은 화면마다 다르지만(StudentClinic·ParentClinic) 이 세 필드는 같아서
 * 구조로만 받는다.
 */
export function formatClinicSlot(clinic: {
  clinicDate: string;
  startTime: string;
  endTime: string;
}): string {
  return `${clinic.clinicDate.slice(5)} (${dayLabel(clinic.clinicDate)}) ` +
    `${clinic.startTime}~${clinic.endTime}`;
}
