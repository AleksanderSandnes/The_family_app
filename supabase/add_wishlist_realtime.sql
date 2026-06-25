-- Ensure wishlists and wishes tables are published to supabase_realtime
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND tablename = 'wishlists'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.wishlists;
        RAISE NOTICE 'Added wishlists to supabase_realtime publication';
    ELSE
        RAISE NOTICE 'wishlists already in supabase_realtime publication';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND tablename = 'wishes'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.wishes;
        RAISE NOTICE 'Added wishes to supabase_realtime publication';
    ELSE
        RAISE NOTICE 'wishes already in supabase_realtime publication';
    END IF;
END $$;

-- Verify
SELECT tablename FROM pg_publication_tables WHERE pubname = 'supabase_realtime' ORDER BY tablename;
