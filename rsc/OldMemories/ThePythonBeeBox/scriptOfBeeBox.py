import discord
from discord.ext import commands
import music

print("pronto ad outplayare")
cogs = [music]

client = commands.Bot(command_prefix='$', intents = discord.Intents.all())
for i in range(len(cogs)):
    cogs[i].setup(client)




client.run("OTM4NDg3NDcwMzM5ODAxMTY5.YfrAkQ.cgnAD_V_wUNrxYHBHmwsIvYJpgI")