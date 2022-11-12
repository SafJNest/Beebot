import discord
from discord.ext import commands
import youtube_dl

class music(commands.Cog):
    def __init__(self, client):
        self.client = client

    @commands.command()
    async def join(self,ctx):
        if ctx.author.voice is None:
           await ctx.send("BRO ENTRA IN UNA STANZA ONISSA")
        voice_channel = ctx.author.voice.channel
        print(ctx.author.voice.channel)
        if ctx.voice_client is None:
            await voice_channel.connect()
            await ctx.send("weeeeeeeee eccomi")
        elif ctx.voice_client.channel == voice_channel:
            await ctx.send("broooo sono gia' quiiiiii")
        else:
            await ctx.send("zpppppngg teleported into:" + str(voice_channel))
            await ctx.voice_client.move_to(voice_channel)

    @commands.command()
    async def bye(self,ctx):
        username = ctx.message.author.mention
        await ctx.send(str("okay va bene me ne vado..." + username))
        await ctx.voice_client.disconnect()

    @commands.command()
    async def play(self,ctx,url):
        if ctx.voice_client is None:
            await ctx.author.voice.channel.connect()
        else:
            ctx.voice_client.stop()
        FFMPEG_OPTIONS = {'before' : '-reconnect 1 -reconnect_streamed 1 -reconnect_delay_max 5', 'option': '-vn'}
        YDL_OPTIONS = {'format': 'bestaudio'}
        vc = ctx.voice_client

        with youtube_dl.YoutubeDL(YDL_OPTIONS) as ydl:
            info = ydl.extract_info(url, download=False)
        if 'entries' in info:
            url2 = info['entries'][0]["formats"][0]
        elif 'formats' in info:
            url2 = info["formats"][0]
        url = info["webpage_url"]
        stream_url = url2["url"]
        source = discord.FFmpegPCMAudio(executable="ffmpeg-5.0-full_build/bin/ffmpeg.exe", source=stream_url)
        await ctx.send(str("riproducendo la canzone della tigre: " + url))
        vc.play(source)    

    @commands.command()
    async def stop(self, ctx):
        if ctx.voice_client is None:
            await ctx.send("cazzo mi stoppi se non sto facendo nulla")
        else:
            ctx.voice_client.stop()
    
    @commands.command()
    async def pause(self, ctx):
        if ctx.voice_client is None:
            await ctx.send("cazzo mi stoppi se non sto facendo nulla")
        else:
            ctx.voice_client.pause()
            await ctx.send("canzone messa in pausa🐝")

    @commands.command()
    async def resume(self, ctx):
        if ctx.voice_client is None:
            await ctx.send("cazzo mi stoppi se non sto facendo nulla")
        else:
            ctx.voice_client.resume()
            await ctx.send("canzone resumata OIDOZ🐝")



def setup(client):
    client.add_cog(music(client))

    