/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.co.fujielectric.fss.data;

/**
 *
 * @author hamasabu
 */
public class VotResultUsedRules {
    public boolean CleanOffice = true;
    public boolean CleanPdf = true;
    public boolean CleanImages = true;
    public boolean CleanCad = false;
    public boolean ExtractEmls = true;
    public boolean BlockPasswordProtectedArchives = false;
    public boolean BlockPasswordProtectedOffice = true;
    public boolean BlockPasswordProtectedPdfs = true;
    public boolean BlockAllPasswordProtected = true;
    public boolean Blockunsupported = false;
    public boolean ScanVirus = false;
    public boolean BlockUnknownFiles = false;
    public boolean BlockFakeFiles = false;
    public boolean ExtractArchiveFiles = false;
    public boolean BlockEquationOleObject = false;
    public boolean BlockBinaryFiles = false;
    public boolean BlockScriptFiles = false;
}
