package com.safjnest.commands.misc.slash;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import javax.imageio.ImageIO;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import io.nayuki.qrcodegen.QrCode;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;

public class QRCodeSlash extends SlashCommand {

    public QRCodeSlash() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "text", "The text to convert to a qr code.", false)
        );

        commandData.setThings(this);
    }

    public static BufferedImage toImage(QrCode qr, int scale, int border) {
        return toImage(qr, scale, border, 0xFFFFFF, 0x000000);
    }

    public static BufferedImage toImage(QrCode qr, int scale, int border, int lightColor, int darkColor) {
        Objects.requireNonNull(qr);
        if (scale <= 0 || border < 0) {
            throw new IllegalArgumentException("Value out of range");
        }
        if (border > Integer.MAX_VALUE / 2 || qr.size + border * 2L > Integer.MAX_VALUE / scale) {
            throw new IllegalArgumentException("Scale or border too large");
        }
    
        BufferedImage result = new BufferedImage(
          (qr.size + border * 2) * scale, 
          (qr.size + border * 2) * scale, 
          BufferedImage.TYPE_INT_RGB
        );
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                boolean color = qr.getModule(x / scale - border, y / scale - border);
                result.setRGB(x, y, color ? darkColor : lightColor);
            }
        }
        return result;
    }

    public static byte[] toByteArray(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    public static BufferedImage generateQrcode(String barcodeText) throws Exception {
        QrCode qrCode = QrCode.encodeText(barcodeText, QrCode.Ecc.MEDIUM);
        BufferedImage img = toImage(qrCode, 6, 3);
        return img;
    }

    @Override   
    protected void execute(SlashCommandEvent event) {
        String data = event.optString("data", "");
        FileUpload QRCode;
        try {
            QRCode = FileUpload.fromData(toByteArray(generateQrcode(data), "png"), "QRCode.png");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        event.replyEmbeds(new EmbedBuilder()
            .setImage("attachment://" + "QRCode.png")
            .build()
        ).addFiles(QRCode).queue();
    }

    @Override
    protected void execute(CommandEvent event) {
        String data = event.getArgs();
        FileUpload QRCode;
        try {
            QRCode = FileUpload.fromData(toByteArray(generateQrcode(data), "png"), "QRCode.png");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        event.getTextChannel().sendMessageEmbeds(new EmbedBuilder()
        .setImage("attachment://" + "QRCode.png")
        .build()
        ).addFiles(QRCode).queue();
    }
}
