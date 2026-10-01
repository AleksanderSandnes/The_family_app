grant update on public.conversation_participants to authenticated;

update public.conversation_participants
set last_read_at = now()
where last_read_at is null;;
