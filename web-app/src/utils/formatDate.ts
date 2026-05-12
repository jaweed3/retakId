import { formatDistanceToNow, parseISO } from 'date-fns';
import { id } from 'date-fns/locale';

export function formatRelativeTime(iso: string): string {
  try {
    return formatDistanceToNow(parseISO(iso), { addSuffix: true, locale: id });
  } catch {
    return iso;
  }
}
