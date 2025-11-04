-- Migration: Add AI parameters to chats table
-- Date: 2025-11-04
-- Description: Adds temperature, max_tokens, top_p, and system_prompt columns to chats table

-- Add temperature column (nullable, for controlling AI response randomness)
ALTER TABLE chats ADD COLUMN IF NOT EXISTS temperature DOUBLE PRECISION;

-- Add max_tokens column (nullable, for limiting response length)
ALTER TABLE chats ADD COLUMN IF NOT EXISTS max_tokens INTEGER;

-- Add top_p column (nullable, for nucleus sampling)
ALTER TABLE chats ADD COLUMN IF NOT EXISTS top_p DOUBLE PRECISION;

-- Add system_prompt column (nullable, for custom system instructions)
ALTER TABLE chats ADD COLUMN IF NOT EXISTS system_prompt TEXT;

-- Verify the changes
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'chats'
  AND column_name IN ('temperature', 'max_tokens', 'top_p', 'system_prompt')
ORDER BY column_name;
