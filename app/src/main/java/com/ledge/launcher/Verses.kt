package com.ledge.launcher

import java.util.Calendar

// King James Version (public domain). One verse per day: day-of-year picks the verse.
// To add more, just add more lines to this list.
val VERSES: List<Pair<String, String>> = listOf(
    "Psalm 23:1" to "The LORD is my shepherd; I shall not want.",
    "John 3:16" to "For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life.",
    "Philippians 4:13" to "I can do all things through Christ which strengtheneth me.",
    "Proverbs 3:5-6" to "Trust in the LORD with all thine heart; and lean not unto thine own understanding. In all thy ways acknowledge him, and he shall direct thy paths.",
    "Isaiah 41:10" to "Fear thou not; for I am with thee: be not dismayed; for I am thy God: I will strengthen thee; yea, I will help thee; yea, I will uphold thee with the right hand of my righteousness.",
    "Jeremiah 29:11" to "For I know the thoughts that I think toward you, saith the LORD, thoughts of peace, and not of evil, to give you an expected end.",
    "Romans 8:28" to "And we know that all things work together for good to them that love God, to them who are the called according to his purpose.",
    "Joshua 1:9" to "Have not I commanded thee? Be strong and of a good courage; be not afraid, neither be thou dismayed: for the LORD thy God is with thee whithersoever thou goest.",
    "Psalm 46:1" to "God is our refuge and strength, a very present help in trouble.",
    "Matthew 11:28" to "Come unto me, all ye that labour and are heavy laden, and I will give you rest.",
    "Psalm 119:105" to "Thy word is a lamp unto my feet, and a light unto my path.",
    "Romans 12:2" to "And be not conformed to this world: but be ye transformed by the renewing of your mind, that ye may prove what is that good, and acceptable, and perfect, will of God.",
    "Galatians 5:22-23" to "But the fruit of the Spirit is love, joy, peace, longsuffering, gentleness, goodness, faith, meekness, temperance: against such there is no law.",
    "Psalm 27:1" to "The LORD is my light and my salvation; whom shall I fear? the LORD is the strength of my life; of whom shall I be afraid?",
    "Matthew 6:33" to "But seek ye first the kingdom of God, and his righteousness; and all these things shall be added unto you.",
    "Psalm 37:4" to "Delight thyself also in the LORD; and he shall give thee the desires of thine heart.",
    "Proverbs 16:3" to "Commit thy works unto the LORD, and thy thoughts shall be established.",
    "Isaiah 40:31" to "But they that wait upon the LORD shall renew their strength; they shall mount up with wings as eagles; they shall run, and not be weary; and they shall walk, and not faint.",
    "Psalm 121:1-2" to "I will lift up mine eyes unto the hills, from whence cometh my help. My help cometh from the LORD, which made heaven and earth.",
    "John 14:27" to "Peace I leave with you, my peace I give unto you: not as the world giveth, give I unto you. Let not your heart be troubled, neither let it be afraid.",
    "Philippians 4:6" to "Be careful for nothing; but in every thing by prayer and supplication with thanksgiving let your requests be made known unto God.",
    "2 Timothy 1:7" to "For God hath not given us the spirit of fear; but of power, and of love, and of a sound mind.",
    "Hebrews 11:1" to "Now faith is the substance of things hoped for, the evidence of things not seen.",
    "Psalm 34:8" to "O taste and see that the LORD is good: blessed is the man that trusteth in him.",
    "Matthew 5:16" to "Let your light so shine before men, that they may see your good works, and glorify your Father which is in heaven.",
    "Ephesians 2:8" to "For by grace are ye saved through faith; and that not of yourselves: it is the gift of God.",
    "Lamentations 3:22-23" to "It is of the LORD's mercies that we are not consumed, because his compassions fail not. They are new every morning: great is thy faithfulness.",
    "Psalm 91:1" to "He that dwelleth in the secret place of the most High shall abide under the shadow of the Almighty.",
    "1 Peter 5:7" to "Casting all your care upon him; for he careth for you.",
    "Colossians 3:23" to "And whatsoever ye do, do it heartily, as to the Lord, and not unto men.",
    "Micah 6:8" to "He hath shewed thee, O man, what is good; and what doth the LORD require of thee, but to do justly, and to love mercy, and to walk humbly with thy God?",
    "Psalm 118:24" to "This is the day which the LORD hath made; we will rejoice and be glad in it.",
    "Romans 15:13" to "Now the God of hope fill you with all joy and peace in believing, that ye may abound in hope, through the power of the Holy Ghost.",
    "James 1:5" to "If any of you lack wisdom, let him ask of God, that giveth to all men liberally, and upbraideth not; and it shall be given him.",
    "Deuteronomy 31:6" to "Be strong and of a good courage, fear not, nor be afraid of them: for the LORD thy God, he it is that doth go with thee; he will not fail thee, nor forsake thee.",
    "Psalm 30:5" to "For his anger endureth but a moment; in his favour is life: weeping may endure for a night, but joy cometh in the morning.",
    "Matthew 7:7" to "Ask, and it shall be given you; seek, and ye shall find; knock, and it shall be opened unto you.",
    "John 8:12" to "Then spake Jesus again unto them, saying, I am the light of the world: he that followeth me shall not walk in darkness, but shall have the light of life.",
    "Proverbs 18:10" to "The name of the LORD is a strong tower: the righteous runneth into it, and is safe."
)

fun todayVerse(): Pair<String, String> {
    val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    return VERSES[day % VERSES.size]
}
