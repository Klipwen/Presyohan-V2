-- Migration: Update subscription tier defaults with categorized merchant and customer benefits

UPDATE public.subscription_tiers
SET 
    merchant_benefits = '["1 Store Branch", "3 Staffs / Store", "10 Categories / Store", "100 Items / Store", "3 AI Parses / day", "Convert as Notes"]'::jsonb,
    customer_benefits = '["Suking Tindahan Partners: 5 Partners", "Presyohan Store Limit: 5 Stores", "Internet Search Quota: 3 Searches / day"]'::jsonb
WHERE tier_id = 'free';

UPDATE public.subscription_tiers
SET 
    merchant_benefits = '["Up to 10 Stores", "10 Staffs / Store", "25 Categories / Store", "500 Items / Store", "10 AI Parses / day", "Unlocked Customer Pairing (suki)", "Convert to PDF", "Convert to Excel", "Price Cloning & Export"]'::jsonb,
    customer_benefits = '["Suking Tindahan Partners: 15 Partners", "Presyohan Store Limit: 15 Stores", "Internet Search Quota: 15 Searches / day"]'::jsonb
WHERE tier_id = 'pro';

UPDATE public.subscription_tiers
SET 
    merchant_benefits = '["Unlimited Stores", "Unlimited Staff / Store", "Unlimited Categories / Store", "Unlimited Items / Store", "50 AI Parses / day", "Unlimited Customer Pairing (suki)", "Unlimited PDF & Excel Exports", "Unlimited Price Cloning", "24/7 VIP Priority Support"]'::jsonb,
    customer_benefits = '["Unlimited Suking Tindahan Partners", "Unlimited Presyohan Stores", "Unlimited Internet Search Quota"]'::jsonb
WHERE tier_id = 'vip';
