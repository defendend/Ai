-- Migration: Add streaming column to chats table
ALTER TABLE chats ADD COLUMN IF NOT EXISTS streaming BOOLEAN DEFAULT true;
