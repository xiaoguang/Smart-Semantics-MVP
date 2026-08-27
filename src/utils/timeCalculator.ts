import dayjs from 'dayjs';
import type { TimeRule, TimeCalendar } from '../types';

export function calculateDateRange(rule: TimeRule, calendar: TimeCalendar, referenceDate: dayjs.Dayjs = dayjs()): { startDate: string; endDate: string; explanation: string } | null {
  const today = referenceDate;
  const yearStartMonth = calendar.yearStartMonth - 1;

  if (rule.rangeType === 'CUSTOM') {
    if (!rule.fixedStartDate || !rule.fixedEndDate) return null;
    const isRecurring = rule.isRecurring;
    const year = today.year();
    const startStr = isRecurring ? `${year}-${rule.fixedStartDate}` : rule.fixedStartDate;
    const endStr = isRecurring ? `${year}-${rule.fixedEndDate}` : rule.fixedEndDate;
    return { startDate: startStr, endDate: endStr, explanation: `${startStr} 至 ${endStr}${isRecurring ? '（每年重复）' : ''}` };
  }

  if (rule.rangeType === 'HOLIDAY') {
    return null;
  }

  if (rule.rangeType === 'ROLLING') {
    const days = rule.rollingDays || rule.paramDefault || 30;
    const start = today.subtract(days - 1, 'day');
    return { startDate: start.format('YYYY-MM-DD'), endDate: today.format('YYYY-MM-DD'), explanation: `${start.format('YYYY-MM-DD')} 至 ${today.format('YYYY-MM-DD')}（近${days}天）` };
  }

  if (rule.rangeType === 'SINCE_START') {
    if (rule.granularity === 'YEAR') {
      const yearStart = calendar.calendarType === 'FISCAL'
        ? dayjs().year(today.month() < yearStartMonth ? today.year() - 1 : today.year()).month(yearStartMonth).date(calendar.yearStartDay)
        : today.startOf('year');
      return { startDate: yearStart.format('YYYY-MM-DD'), endDate: today.format('YYYY-MM-DD'), explanation: `${yearStart.format('YYYY-MM-DD')} 至 ${today.format('YYYY-MM-DD')}（年初至今）` };
    }
    if (rule.granularity === 'QUARTER') {
      const q = Math.floor(today.month() / 3);
      const qStart = today.month(q * 3).date(1);
      return { startDate: qStart.format('YYYY-MM-DD'), endDate: today.format('YYYY-MM-DD'), explanation: `${qStart.format('YYYY-MM-DD')} 至 ${today.format('YYYY-MM-DD')}（季度初至今）` };
    }
    if (rule.granularity === 'MONTH') {
      const mStart = today.startOf('month');
      return { startDate: mStart.format('YYYY-MM-DD'), endDate: today.format('YYYY-MM-DD'), explanation: `${mStart.format('YYYY-MM-DD')} 至 ${today.format('YYYY-MM-DD')}（月初至今）` };
    }
    return null;
  }

  if (rule.granularity === 'YEAR') {
    let yearStart: dayjs.Dayjs;
    if (calendar.calendarType === 'FISCAL') {
      let fy = today.year();
      if (today.month() < yearStartMonth) fy--;
      yearStart = dayjs().year(fy).month(yearStartMonth).date(calendar.yearStartDay);
    } else {
      yearStart = today.startOf('year');
    }
    const offset = rule.offsetValue || 0;
    if (offset !== 0) {
      yearStart = yearStart.add(offset, 'year');
    }
    const yearEnd = yearStart.add(1, 'year').subtract(1, 'day');
    const end = offset < 0 ? yearEnd : today;
    return { startDate: yearStart.format('YYYY-MM-DD'), endDate: end.format('YYYY-MM-DD'), explanation: `${yearStart.format('YYYY-MM-DD')} 至 ${end.format('YYYY-MM-DD')}` };
  }

  if (rule.granularity === 'QUARTER') {
    const q = Math.floor(today.month() / 3);
    let qStart = today.month(q * 3).date(1).startOf('month');
    const offset = rule.offsetValue || 0;
    if (offset !== 0) {
      qStart = qStart.add(offset * 3, 'month');
    }
    const qEnd = qStart.add(3, 'month').subtract(1, 'day');
    const end = offset < 0 ? qEnd : today;
    return { startDate: qStart.format('YYYY-MM-DD'), endDate: end.format('YYYY-MM-DD'), explanation: `${qStart.format('YYYY-MM-DD')} 至 ${end.format('YYYY-MM-DD')}` };
  }

  if (rule.granularity === 'MONTH') {
    let mStart = today.startOf('month');
    const offset = rule.offsetValue || 0;
    if (offset !== 0) {
      mStart = mStart.add(offset, 'month');
    }
    const mEnd = mStart.endOf('month');
    const end = offset < 0 ? mEnd : today;
    return { startDate: mStart.format('YYYY-MM-DD'), endDate: end.format('YYYY-MM-DD'), explanation: `${mStart.format('YYYY-MM-DD')} 至 ${end.format('YYYY-MM-DD')}` };
  }

  if (rule.granularity === 'WEEK') {
    let wStart = today.startOf('week').add(1, 'day');
    const offset = rule.offsetValue || 0;
    if (offset !== 0) {
      wStart = wStart.add(offset * 7, 'day');
    }
    const wEnd = wStart.add(6, 'day');
    const end = offset < 0 ? wEnd : today;
    return { startDate: wStart.format('YYYY-MM-DD'), endDate: end.format('YYYY-MM-DD'), explanation: `${wStart.format('YYYY-MM-DD')} 至 ${end.format('YYYY-MM-DD')}` };
  }

  if (rule.granularity === 'DAY') {
    const offset = rule.offsetValue || 0;
    const target = today.add(offset, 'day');
    return { startDate: target.format('YYYY-MM-DD'), endDate: target.format('YYYY-MM-DD'), explanation: target.format('YYYY-MM-DD') };
  }

  return null;
}

export function getPreviewText(rule: TimeRule, calendar: TimeCalendar): string {
  if (rule.rangeType === 'HOLIDAY') {
    return `查找 ${rule.holidayCode} 在当年的日期区间`;
  }
  if (rule.status === 'INACTIVE') {
    return '已禁用';
  }
  const result = calculateDateRange(rule, calendar);
  if (!result) return '—';
  return result.explanation;
}
